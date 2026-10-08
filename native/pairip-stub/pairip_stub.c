#include <jni.h>
#include <android/log.h>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "gpp_pairip_stub", __VA_ARGS__)

JNIEXPORT jobject JNICALL
Java_com_pairip_VMRunner_executeVM(JNIEnv *env, jclass clazz, jbyteArray vmCode, jobjectArray args) {
    (void)env; (void)clazz; (void)vmCode; (void)args;
    LOGI("executeVM stubbed");
    return NULL;
}

JNIEXPORT jint JNI_OnLoad(JavaVM *vm, void *reserved) {
    (void)vm; (void)reserved;
    LOGI("JNI_OnLoad stub");
    return JNI_VERSION_1_6;
}
