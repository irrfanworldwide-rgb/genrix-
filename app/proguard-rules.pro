# Release builds shrink the code (R8). These libraries find Java classes and methods by name
# from native code or by reflection, so their names must survive.

# Vosk (local commands) talks to its native library through JNA.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class org.vosk.** { *; }
-dontwarn java.awt.**

# ONNX Runtime (wake word) calls back into its Java classes from native code.
-keep class ai.onnxruntime.** { *; }

# Our SpeexDSP echo canceller: the JNI functions are named after this class and its methods.
-keep class app.genrix.android.audio.EchoCanceller { *; }
