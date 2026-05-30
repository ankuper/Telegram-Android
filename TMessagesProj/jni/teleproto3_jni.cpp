/*
 * teleproto3_jni.cpp — JNI bridge for the Type3 SOCKS5 shim (Story 9-1).
 *
 * Exposed to Java as org.telegram.messenger.Type3ShimController native methods.
 * One global shim handle; thread-safe via Java-side synchronization.
 */

#include <jni.h>
#include <android/log.h>
// Must define before any t3 headers so t3_features.h sees it
#define T3_SHIM_SOCKS5_AVAILABLE 1
#include "teleproto3/include/t3.h"
#include "teleproto3/include/t3_shim_socks5.h"

#define LOG_TAG "T3Shim"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static t3_shim_t *g_shim = nullptr;

extern "C" {

/*
 * Type3ShimController.nativeStart(host, port, wsPath, secretHex) → int shimPort
 * Returns 0 on failure.
 */
JNIEXPORT jint JNICALL
Java_org_telegram_messenger_Type3ShimController_nativeStart(
        JNIEnv *env, jclass /*cls*/,
        jstring jHost, jint port, jstring jWsPath, jstring jSecretHex) {

    if (g_shim) {
        t3_shim_close(g_shim);
        g_shim = nullptr;
    }

    const char *host      = env->GetStringUTFChars(jHost,      nullptr);
    const char *wsPath    = env->GetStringUTFChars(jWsPath,    nullptr);
    const char *secretHex = env->GetStringUTFChars(jSecretHex, nullptr);

    t3_shim_t   *shim = nullptr;
    t3_result_t  rc   = t3_shim_open(host, (uint16_t)port, wsPath, secretHex, 0, &shim);

    env->ReleaseStringUTFChars(jHost,      host);
    env->ReleaseStringUTFChars(jWsPath,    wsPath);
    env->ReleaseStringUTFChars(jSecretHex, secretHex);

    if (rc != T3_OK || !shim) {
        LOGE("t3_shim_open failed: rc=%d", rc);
        return 0;
    }

    g_shim = shim;
    uint16_t p = t3_shim_local_port(shim);
    LOGI("shim started on 127.0.0.1:%u", p);
    return (jint)p;
}

/*
 * Type3ShimController.nativeStop()
 */
JNIEXPORT void JNICALL
Java_org_telegram_messenger_Type3ShimController_nativeStop(
        JNIEnv * /*env*/, jclass /*cls*/) {
    if (g_shim) {
        t3_shim_close(g_shim);
        g_shim = nullptr;
        LOGI("shim stopped");
    }
}

/*
 * Type3ShimController.nativeCredentials() → String[2] {user, pass} or null
 */
JNIEXPORT jobjectArray JNICALL
Java_org_telegram_messenger_Type3ShimController_nativeCredentials(
        JNIEnv *env, jclass /*cls*/) {
    if (!g_shim) return nullptr;

    char user[T3_SHIM_CRED_BUFLEN];
    char pass[T3_SHIM_CRED_BUFLEN];
    if (t3_shim_get_credentials(g_shim, user, sizeof(user), pass, sizeof(pass)) != T3_OK) {
        LOGE("t3_shim_get_credentials failed");
        return nullptr;
    }

    jclass    strClass = env->FindClass("java/lang/String");
    jobjectArray result = env->NewObjectArray(2, strClass, nullptr);
    env->SetObjectArrayElement(result, 0, env->NewStringUTF(user));
    env->SetObjectArrayElement(result, 1, env->NewStringUTF(pass));
    return result;
}

} // extern "C"
