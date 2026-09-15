package com.fortrow.jflux.jni;

/** Compact entry point for the common JNI platform, loading, and build operations. */
public final class JNI {

    private JNI() { }

    /** Returns the current normalized native platform. */
    public static JNIPlatform platform() {
        return JNIPlatform.current();
    }

    /** Loads a logical native library using the default resource strategy. */
    public static NativeLibraryLoadResult load(String logicalName) {
        return NativeLibraryLoader.load(logicalName);
    }

    /** Starts a portable JNI build specification for a logical library name. */
    public static JNIBuilder builder(String logicalName) {
        return JNIBuilder.forLibrary(logicalName);
    }

    /** Generates JNI C declarations for native methods declared by a class. */
    public static String header(Class<?> nativeClass) {
        return JNIHeaderGenerator.generate(nativeClass);
    }

    /** Generates a compilable C stub template for native methods declared by a class. */
    public static String sourceTemplate(Class<?> nativeClass) {
        return JNIHeaderGenerator.generateSourceTemplate(nativeClass);
    }
}
