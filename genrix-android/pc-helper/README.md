# Genrix PC helper

Lets the Genrix phone app do a **fixed set of things** on your computer: open listed apps or
links, volume and media keys, lock the PC, and (if you switch it on) sleep, restart or shut down.
It cannot run arbitrary commands.

## Run it

1. Install Python 3.8 or newer on the PC (nothing else to install; `psutil` is optional and
   only adds battery info).
2. `python genrix_pc.py`
3. First run creates `genrix_pc.json` with a secret token and prints an **Address** and **Token**.
4. On the phone: Genrix, Settings, PC bridge: enter both, tap **Save and test**.
5. Windows asks to allow Python through the firewall the first time: allow it on **Private** networks.

Phone and PC must be on the same Wi-Fi (or both on Tailscale).

## Config (`genrix_pc.json`)

- `apps`: names Genrix may open, each mapped to a program, e.g. `"chrome": "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe"`.
- `allow_power`: `false` by default. Lock always works; sleep, restart and shutdown need `true`.
  Windows restart/shutdown start a 30 s countdown; `shutdown /a` cancels it.
- `port`: default 8765. Keep `genrix_pc.json` private: the token is the key.

## How it is protected

- Each request is signed (HMAC-SHA256 over method, path, time, a one-time nonce and the body).
  Wrong signature, a clock more than 2 minutes off, or a repeated nonce is rejected.
- Traffic is plain http, so only use it on Wi-Fi you trust. Away from home, use Tailscale.
- Power actions also need your Allow tap on the phone.
