# Wake-word models (not included)

The wake word (the pretrained model hears "Hey Jarvis"; a "Hey Genrix" phrase needs your own trained model) needs three ONNX models in this folder:

| File | Source | Licence |
|------|--------|---------|
| `melspectrogram.onnx` | openWakeWord v0.5.1 release assets | Apache-2.0 project |
| `embedding_model.onnx` | Google speech_embedding, converted by openWakeWord | Apache-2.0 |
| `hey_jarvis_v0.1.onnx` | openWakeWord v0.5.1 release assets | **CC BY-NC-SA 4.0, non-commercial** |

They are not in this repository or the published APK, because the word model's licence
doesn't allow redistribution in this project. Without them the app hides the wake word and
everything else works. For your own non-commercial debug build you can download them from
the openWakeWord releases (github.com/dscripka/openWakeWord) into this folder. A wake-word
model this project may ship will be trained separately.
