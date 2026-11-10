// JNI bridge to SpeexDSP's echo canceller and residual-echo suppressor (audio/EchoCanceller.kt).
#include <jni.h>
#include <stdlib.h>
#include "speex/speex_echo.h"
#include "speex/speex_preprocess.h"

typedef struct {
    SpeexEchoState *echo;
    SpeexPreprocessState *pre;
    int frame;
} Aec;

JNIEXPORT jlong JNICALL
Java_app_genrix_android_audio_EchoCanceller_nativeCreate(JNIEnv *env, jclass cls, jint frame, jint tail, jint rate) {
    Aec *aec = calloc(1, sizeof(Aec));
    if (!aec) return 0;
    aec->frame = frame;
    aec->echo = speex_echo_state_init(frame, tail);
    aec->pre = speex_preprocess_state_init(frame, rate);
    speex_echo_ctl(aec->echo, SPEEX_ECHO_SET_SAMPLING_RATE, &rate);
    speex_preprocess_ctl(aec->pre, SPEEX_PREPROCESS_SET_ECHO_STATE, aec->echo);
    int on = 1;
    speex_preprocess_ctl(aec->pre, SPEEX_PREPROCESS_SET_DENOISE, &on);
    return (jlong) (intptr_t) aec;
}

// mic, ref, out and suppressed hold the same whole number of frames. out is the echo
// canceller's output (a voice over the reply survives it); suppressed has the leftover echo
// and noise removed too, which also mutes a voice that overlaps the reply.
JNIEXPORT void JNICALL
Java_app_genrix_android_audio_EchoCanceller_nativeProcess(JNIEnv *env, jclass cls, jlong handle, jshortArray mic, jshortArray ref, jshortArray out, jshortArray suppressed) {
    Aec *aec = (Aec *) (intptr_t) handle;
    if (!aec) return;
    jsize n = (*env)->GetArrayLength(env, mic);
    jshort *m = (*env)->GetShortArrayElements(env, mic, NULL);
    jshort *r = (*env)->GetShortArrayElements(env, ref, NULL);
    jshort *o = (*env)->GetShortArrayElements(env, out, NULL);
    jshort *q = (*env)->GetShortArrayElements(env, suppressed, NULL);
    for (int i = 0; i + aec->frame <= n; i += aec->frame) {
        speex_echo_cancellation(aec->echo, m + i, r + i, o + i);
        for (int k = 0; k < aec->frame; k++) q[i + k] = o[i + k];
        speex_preprocess_run(aec->pre, q + i);
    }
    (*env)->ReleaseShortArrayElements(env, mic, m, JNI_ABORT);
    (*env)->ReleaseShortArrayElements(env, ref, r, JNI_ABORT);
    (*env)->ReleaseShortArrayElements(env, out, o, 0);
    (*env)->ReleaseShortArrayElements(env, suppressed, q, 0);
}

JNIEXPORT void JNICALL
Java_app_genrix_android_audio_EchoCanceller_nativeReset(JNIEnv *env, jclass cls, jlong handle) {
    Aec *aec = (Aec *) (intptr_t) handle;
    if (aec) speex_echo_state_reset(aec->echo);
}

JNIEXPORT void JNICALL
Java_app_genrix_android_audio_EchoCanceller_nativeDestroy(JNIEnv *env, jclass cls, jlong handle) {
    Aec *aec = (Aec *) (intptr_t) handle;
    if (!aec) return;
    speex_preprocess_state_destroy(aec->pre);
    speex_echo_state_destroy(aec->echo);
    free(aec);
}
