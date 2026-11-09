#!/usr/bin/env python3
"""Genrix PC helper: lets the Genrix phone app do a fixed set of things on this computer.

Python 3.8+, standard library only. Run:  python genrix_pc.py

Safety design
- No arbitrary commands. Only these actions exist: status, media, open, power.
- "open" runs only apps listed in genrix_pc.json ("apps") or opens an http(s) link.
- Power: "lock" is always possible; sleep/restart/shutdown only if "allow_power" is true.
- Every request must carry an HMAC-SHA256 signature made with your secret token, with a
  timestamp and a one-time nonce, so captured requests can't be replayed.
- Plain HTTP on your home network: use it on Wi-Fi you trust, or over Tailscale.
"""
import argparse
import hashlib
import hmac
import json
import os
import platform
import secrets
import socket
import subprocess
import sys
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

HERE = os.path.dirname(os.path.abspath(__file__))
CONFIG_PATH = os.path.join(HERE, "genrix_pc.json")
SYSTEM = platform.system()  # "Windows", "Darwin" or "Linux"
MAX_SKEW_S = 120
NONCE_TTL_S = 300
MAX_BODY = 4096
MAX_PER_MINUTE = 40

DEFAULT_APPS = {
    "Windows": {
        "notepad": "notepad.exe",
        "calculator": "calc.exe",
        "paint": "mspaint.exe",
        "file explorer": "explorer.exe",
        "task manager": "taskmgr.exe",
    },
    "Darwin": {"notes": "Notes", "calculator": "Calculator", "terminal": "Terminal", "safari": "Safari"},
    "Linux": {"calculator": "gnome-calculator", "files": "nautilus", "terminal": "gnome-terminal"},
}


def load_config():
    if os.path.exists(CONFIG_PATH):
        with open(CONFIG_PATH, "r", encoding="utf-8") as f:
            cfg = json.load(f)
    else:
        cfg = {}
    changed = False
    if not cfg.get("token"):
        cfg["token"] = secrets.token_urlsafe(24)
        changed = True
    cfg.setdefault("port", 8765)
    cfg.setdefault("allow_power", False)
    if "apps" not in cfg:
        cfg["apps"] = DEFAULT_APPS.get(SYSTEM, {})
        changed = True
    if changed or not os.path.exists(CONFIG_PATH):
        with open(CONFIG_PATH, "w", encoding="utf-8") as f:
            json.dump(cfg, f, indent=2)
        try:
            os.chmod(CONFIG_PATH, 0o600)
        except OSError:
            pass
    return cfg


def lan_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))  # no packet is sent
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


# ---------------------------------------------------------------- actions
def run(cmd):
    return subprocess.run(cmd, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, timeout=10).returncode == 0


def act_status(cfg, args):
    parts = [f"{socket.gethostname()} ({SYSTEM} {platform.release()}) is on"]
    try:
        import psutil  # optional

        battery = psutil.sensors_battery()
        if battery is not None:
            parts.append(f"battery {int(battery.percent)}%{' charging' if battery.power_plugged else ''}")
    except Exception:
        pass
    return True, ", ".join(parts)


WIN_KEYS = {"volume_up": 0xAF, "volume_down": 0xAE, "mute": 0xAD, "play_pause": 0xB3, "next": 0xB0, "previous": 0xB1}


def press_windows_key(vk):
    import ctypes

    user32 = ctypes.windll.user32
    user32.keybd_event(vk, 0, 0, 0)
    user32.keybd_event(vk, 0, 2, 0)  # KEYEVENTF_KEYUP


def act_media(cfg, args):
    key = str(args.get("key", ""))
    times = max(1, min(int(args.get("times", 1) or 1), 10))
    if key not in WIN_KEYS:
        return False, "Unknown media key."
    if SYSTEM == "Windows":
        for _ in range(times):
            press_windows_key(WIN_KEYS[key])
        return True, f"Pressed {key.replace('_', ' ')}" + (f" x{times}" if times > 1 else "") + "."
    if SYSTEM == "Darwin" and key in ("volume_up", "volume_down", "mute"):
        if key == "mute":
            ok = run(["osascript", "-e", "set volume output muted not (output muted of (get volume settings))"])
        else:
            step = 6 * times * (1 if key == "volume_up" else -1)
            ok = run(["osascript", "-e", f"set volume output volume ((output volume of (get volume settings)) + {step})"])
        return ok, "Done." if ok else "macOS refused the volume change."
    if SYSTEM == "Linux":
        if key in ("volume_up", "volume_down"):
            sign = "+" if key == "volume_up" else "-"
            ok = run(["pactl", "set-sink-volume", "@DEFAULT_SINK@", f"{sign}{5 * times}%"])
        elif key == "mute":
            ok = run(["pactl", "set-sink-mute", "@DEFAULT_SINK@", "toggle"])
        else:
            ok = run(["playerctl", {"play_pause": "play-pause", "next": "next", "previous": "previous"}[key]])
        return ok, "Done." if ok else "That needs pactl or playerctl installed."
    return False, f"{key.replace('_', ' ')} isn't supported on {SYSTEM}."


def act_open(cfg, args):
    target = str(args.get("target", "")).strip()
    if not target:
        return False, "Nothing to open."
    if target.lower().startswith(("http://", "https://")):
        if SYSTEM == "Windows":
            os.startfile(target)  # noqa: opens the default browser
        elif SYSTEM == "Darwin":
            run(["open", target])
        else:
            run(["xdg-open", target])
        return True, "Opened the link on the PC."
    apps = {k.lower(): v for k, v in cfg.get("apps", {}).items()}
    cmd = apps.get(target.lower())
    if not cmd:
        names = ", ".join(sorted(apps)) or "none set up"
        return False, f"'{target}' isn't in the PC's app list. Available: {names}."
    if SYSTEM == "Darwin":
        subprocess.Popen(["open", "-a", cmd])
    elif SYSTEM == "Windows":
        subprocess.Popen(cmd, shell=False)
    else:
        subprocess.Popen([cmd], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    return True, f"Opened {target} on the PC."


def act_power(cfg, args):
    action = str(args.get("action", ""))
    if action != "lock" and not cfg.get("allow_power"):
        return False, f"{action} is switched off in the PC helper (allow_power in genrix_pc.json)."
    table = {
        "Windows": {
            "lock": ["rundll32.exe", "user32.dll,LockWorkStation"],
            "sleep": ["rundll32.exe", "powrprof.dll,SetSuspendState", "0,1,0"],
            "restart": ["shutdown", "/r", "/t", "30"],
            "shutdown": ["shutdown", "/s", "/t", "30"],
        },
        "Darwin": {
            "lock": ["pmset", "displaysleepnow"],
            "sleep": ["pmset", "sleepnow"],
            "restart": ["osascript", "-e", 'tell app "System Events" to restart'],
            "shutdown": ["osascript", "-e", 'tell app "System Events" to shut down'],
        },
        "Linux": {
            "lock": ["loginctl", "lock-session"],
            "sleep": ["systemctl", "suspend"],
            "restart": ["shutdown", "-r", "+1"],
            "shutdown": ["shutdown", "-h", "+1"],
        },
    }
    cmd = table.get(SYSTEM, {}).get(action)
    if not cmd:
        return False, f"{action} isn't supported on {SYSTEM}."
    ok = run(cmd)
    extra = " It happens in about 30 seconds; run 'shutdown /a' on the PC to cancel." if SYSTEM == "Windows" and action in ("restart", "shutdown") else ""
    return ok, (f"PC {action} started.{extra}" if ok else f"The PC refused {action}.")


ACTIONS = {"status": act_status, "media": act_media, "open": act_open, "power": act_power}


# ---------------------------------------------------------------- server
class Guard:
    """Signature check, replay protection and a small rate limit."""

    def __init__(self, token):
        self.token = token.encode()
        self.seen = {}
        self.hits = []
        self.lock = threading.Lock()

    def check(self, path, ts, nonce, sig, body):
        try:
            if abs(time.time() - int(ts)) > MAX_SKEW_S:
                return False
        except (TypeError, ValueError):
            return False
        if not nonce or len(nonce) > 64 or not sig:
            return False
        msg = f"POST\n{path}\n{ts}\n{nonce}\n".encode() + body
        good = hmac.new(self.token, msg, hashlib.sha256).hexdigest()
        if not hmac.compare_digest(good, sig):
            return False
        now = time.time()
        with self.lock:
            self.seen = {n: t for n, t in self.seen.items() if now - t < NONCE_TTL_S}
            if nonce in self.seen:
                return False
            self.seen[nonce] = now
        return True

    def rate_ok(self):
        now = time.time()
        with self.lock:
            self.hits = [t for t in self.hits if now - t < 60]
            if len(self.hits) >= MAX_PER_MINUTE:
                return False
            self.hits.append(now)
            return True


def make_handler(cfg, guard):
    class Handler(BaseHTTPRequestHandler):
        server_version = "GenrixPC"

        def log_message(self, fmt, *a):  # never log tokens or bodies
            pass

        def reply(self, code, ok, message):
            data = json.dumps({"ok": ok, "message": message}).encode()
            self.send_response(code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(data)))
            self.end_headers()
            self.wfile.write(data)

        def do_POST(self):
            if not guard.rate_ok():
                return self.reply(429, False, "Too many requests.")
            length = int(self.headers.get("Content-Length", "0") or 0)
            if length > MAX_BODY:
                return self.reply(413, False, "Request too large.")
            body = self.rfile.read(length) if length else b""
            if not guard.check(self.path, self.headers.get("X-Genrix-Ts"), self.headers.get("X-Genrix-Nonce"),
                               self.headers.get("X-Genrix-Sig"), body):
                print(f"[{time.strftime('%H:%M:%S')}] rejected a request (bad signature, old, or replayed)")
                return self.reply(401, False, "Unauthorized.")
            action = self.path[len("/v1/"):] if self.path.startswith("/v1/") else ""
            fn = ACTIONS.get(action)
            if fn is None:
                return self.reply(404, False, "Unknown action.")
            try:
                args = json.loads(body.decode("utf-8") or "{}")
                ok, message = fn(cfg, args if isinstance(args, dict) else {})
            except Exception as e:  # keep the server alive, say what class of error
                ok, message = False, f"Helper error: {type(e).__name__}"
            print(f"[{time.strftime('%H:%M:%S')}] {action}: {'ok' if ok else 'failed'}")
            self.reply(200, ok, message)

        def do_GET(self):
            self.reply(405, False, "Use the Genrix app.")

    return Handler


def main():
    ap = argparse.ArgumentParser(description="Genrix PC helper")
    ap.add_argument("--host", default="0.0.0.0")
    ap.add_argument("--port", type=int)
    opts = ap.parse_args()
    cfg = load_config()
    port = opts.port or cfg["port"]
    guard = Guard(cfg["token"])
    server = ThreadingHTTPServer((opts.host, port), make_handler(cfg, guard))
    print("Genrix PC helper is running.\n")
    print("Put these in the Genrix app: Settings -> PC bridge")
    print(f"  Address: http://{lan_ip()}:{port}")
    print(f"  Token:   {cfg['token']}\n")
    print(f"Apps Genrix can open: {', '.join(sorted(cfg['apps'])) or 'none'} (edit genrix_pc.json to add more)")
    print(f"Sleep / restart / shutdown: {'ON' if cfg['allow_power'] else 'off (set allow_power to true to enable)'}")
    print("Press Ctrl+C to stop.")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nStopped.")


if __name__ == "__main__":
    main()
