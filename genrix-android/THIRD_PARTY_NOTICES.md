# Third-party notices

## Included in the app

| Component | Version | Licence | How it's used |
|---|---|---|---|
| AndroidX (Core, Activity, Lifecycle, Compose, Material 3) | see `gradle/libs.versions.toml` | Apache-2.0 | App framework and screens |
| Kotlin standard library | 2.0.21 | Apache-2.0 | Language runtime |
| OkHttp | 4.12.0 | Apache-2.0 | Network requests |
| ONNX Runtime for Android | 1.20.0 | MIT | Wake-word engine (only active when a wake-word model is added) |
| Vosk Android | 0.3.75 | Apache-2.0 | Offline speech for simple commands |
| JNA (through Vosk) | 5.x | Apache-2.0 / LGPL-2.1 (dual) | Used under Apache-2.0 |
| `vosk-model-small-en-in-0.4` | 0.4 | Apache-2.0 (per alphacephei.com/vosk/models) | Downloaded at build time, checked against a pinned SHA-256, not stored in git |
| SpeexDSP | 1.2.1 | BSD-3-Clause | Echo canceller, compiled unchanged from `app/src/main/cpp/speexdsp/` (licence in its `COPYING`) |

## Services the app talks to

| Service | When | Terms |
|---|---|---|
| Google Gemini API | Your requests, with **your own** API key | Google's Gemini API terms |
| Open-Meteo (weather and city lookup) | When you ask about the weather | Data CC BY 4.0. Open-Meteo's free API is for non-commercial use; Genrix is a free, non-commercial project. A commercial fork must use a paid plan or another provider. |

## Not included

- **openWakeWord "hey_jarvis" model**: CC BY-NC-SA 4.0 (non-commercial), so it is not part of
  this repository or the published APK.
- No other AI assistant's code is included. The ideas for grid tapping and the untrusted-text
  guard were inspired by reading about other open-source phone agents; the code was written new.
