/*
 * Cross-platform JNI bridge template.
 *
 * Keep the implementation within the JNI API when possible. The same source
 * can then be built on Windows, macOS, Linux, and FreeBSD by JNIBuilder.
 */
#include <jni.h>

JNIEXPORT jstring JNICALL Java_com_example_NativeBridge_echo
  (JNIEnv *env, jobject self, jstring value) {
    (void) self;
    if (value == NULL) return NULL;

    /* Replace this body with the native operation and check JNI exceptions. */
    return (*env)->NewLocalRef(env, value);
}
