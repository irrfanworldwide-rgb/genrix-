# Genrix for Android

A voice assistant for your Android phone that talks in **English, Hindi and Hinglish**, uses your
apps for you, and **asks before anything risky**. It runs on Google Gemini with **your own free
API key**: no account, no subscription, no other AI app needed.

> **Download:** the latest signed APK is on the [Releases](../../releases) page.
> Install steps: [INSTALL.md](INSTALL.md).

## What it can do

- **Talk:** tap the floating orb over any app (or open Genrix) and speak; double-tap to type.
  Interrupt it any time by talking over it or waving a hand over the phone.
- **Your messages:** "What did I miss?" (people first, codes hidden, scam links flagged),
  "keep an eye on my messages" (it says only *who* messaged, never *what*), and hands-free
  replies: it reads your reply back and sends only after **you** say "yes" / "haan".
- **Calls, WhatsApp, SMS, email:** opens the right app with everything filled in; you tap
  Send or Call.
- **Calendar:** "what's on today?", "add a meeting tomorrow at 4" (you tap Save).
- **Memory and routines:** "remember I parked on level 2", "good morning" (time, weather,
  messages, calendar), or make your own routine by voice.
- **Use any app** (with Phone control on): reads the screen, taps, types and scrolls; can tap
  image-only buttons by looking at the screen.
- **Your PC (optional):** with the small helper in [pc-helper/](pc-helper/README.md), say "PC pe
  Chrome kholo", "laptop ki awaaz kam karo" or "PC lock kar do". Fixed actions only, signed
  requests, power actions need your Allow tap.
- **Phone basics:** alarms, timers, reminders, music, volume, flashlight, weather, web search.

## Safety rules (in code, not just in the AI's instructions)

- It **never pays**, types a PIN, password or OTP, or opens banking, UPI, password-manager,
  Play Store or Settings apps. At those steps it shows a **"Your turn"** card and waits for you.
- Send, Call, Delete, Book and similar taps show an **Allow** card that only your finger can press.
- Text from other people (screens, messages, emails, invites) can't make it act: after reading
  it, calls, messages, bookings and memory changes need your tap.
- Memory refuses anything that looks like a password, PIN, OTP or card number.

See [PRIVACY.md](PRIVACY.md) for exactly what leaves the phone.

## What you need

- Android 10 or newer (Android 11+ for "tap by sight").
- A free **Gemini API key** from [Google AI Studio](https://aistudio.google.com/apikey).
  The free tier has daily limits; on the free tier Google may use requests to improve its
  products. Turning on billing for your key raises the limits and stops that.

## Build it yourself

Requirements: JDK 17 and the Android SDK (platform 35, NDK 27.0.12077973, CMake 3.22.1).

```bash
./gradlew :app:testDebugUnitTest   # unit tests
./gradlew :app:assembleDebug       # debug APK in app/build/outputs/apk/debug/
```

The first build downloads the offline speech model (Vosk, Apache-2.0) and checks it against a
pinned SHA-256. Release builds are signed with a key passed in through the `GENRIX_KEYSTORE`
and `GENRIX_KEYSTORE_PASSWORD` environment variables; without them the release APK is unsigned.

The wake word is **not included** (the only ready-made model hears "Hey Jarvis"; a "Hey Genrix" phrase needs a model you train yourself): the only available model is licensed for
non-commercial use. Without it the app simply hides the wake word; see
[app/src/debug/assets/wake/README.md](app/src/debug/assets/wake/README.md).

## Design

See [design/](design/README.md) for the interactive preview and the Figma link.

## Licence

Genrix is a modified version of "JARVIS for Android" by h4harshill (GPL-3.0-or-later); see [NOTICE](NOTICE).

[GNU GPL v3.0 or later](LICENSE): you may use, study, change and share Genrix, even
commercially, but if you share a changed version you must publish its full source code under
the same licence. Third-party parts and their licences: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Genrix is an independent project. It is not affiliated with or endorsed by Google,
WhatsApp or any other company named here.
