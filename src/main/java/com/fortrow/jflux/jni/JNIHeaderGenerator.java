package com.fortrow.jflux.jni;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Generates portable JNI C prototypes from Java methods declared {@code native}. */
public final class JNIHeaderGenerator {

    private JNIHeaderGenerator() { }

    /** Generates a guarded C header for all native methods declared by a class. */
    public static String generate(Class<?> nativeClass) {
        if (nativeClass == null) throw ExceptionHandling.invalid("A native Java class is required.");
        var methods = nativeMethods(nativeClass);
        if (methods.isEmpty()) {
            throw ExceptionHandling.invalid("Class does not declare native methods: " + nativeClass.getName());
        }

        String guard = includeGuard(nativeClass);
        Map<String, Long> counts = methods.stream()
                .collect(Collectors.groupingBy(Method::getName, Collectors.counting()));
        StringBuilder header = new StringBuilder(512);
        header.append("#ifndef ").append(guard).append('\n')
                .append("#define ").append(guard).append("\n\n")
                .append("#include <jni.h>\n\n")
                .append("#ifdef __cplusplus\nextern \"C\" {\n#endif\n\n");
        for (Method method : methods) {
            appendPrototype(header, nativeClass, method, counts.get(method.getName()) > 1);
            header.append(';').append('\n');
        }
        header.append("\n#ifdef __cplusplus\n}\n#endif\n\n#endif /* ").append(guard).append(" */\n");
        return header.toString();
    }

    /** Generates a C implementation stub for every native method in a class. */
    public static String generateSourceTemplate(Class<?> nativeClass) {
        String header = generate(nativeClass);
        var methods = nativeMethods(nativeClass);
        Map<String, Long> counts = methods.stream()
                .collect(Collectors.groupingBy(Method::getName, Collectors.counting()));
        StringBuilder source = new StringBuilder(header.length() + methods.size() * 220);
        source.append("#include <jni.h>\n#include <stddef.h>\n\n");
        for (Method method : methods) {
            appendPrototype(source, nativeClass, method, counts.get(method.getName()) > 1);
            source.append(" {\n    (void) env;\n    (void) self;\n");
            for (int index = 0; index < method.getParameterCount(); index++) {
                source.append("    (void) arg").append(index).append(";\n");
            }
            appendDefaultReturn(source, method.getReturnType());
            source.append("}\n\n");
        }
        return source.toString();
    }

    /** Returns the JNI symbol for a native method, including overload encoding when needed. */
    public static String jniSymbol(Class<?> nativeClass, Method method) {
        if (nativeClass == null || method == null) throw ExceptionHandling.invalid("Native class and method are required.");
        if (!Modifier.isNative(method.getModifiers())) throw ExceptionHandling.invalid("Method is not native: " + method);
        long sameName = Arrays.stream(nativeClass.getDeclaredMethods())
                .filter(candidate -> Modifier.isNative(candidate.getModifiers()))
                .filter(candidate -> candidate.getName().equals(method.getName()))
                .count();
        return jniSymbol(nativeClass, method, sameName > 1);
    }

    private static java.util.List<Method> nativeMethods(Class<?> nativeClass) {
        return Arrays.stream(nativeClass.getDeclaredMethods())
                .filter(method -> Modifier.isNative(method.getModifiers()))
                .sorted(Comparator.comparing(Method::getName).thenComparing(JNIHeaderGenerator::descriptor))
                .toList();
    }

    private static void appendPrototype(StringBuilder output, Class<?> nativeClass, Method method, boolean overloaded) {
        output.append("JNIEXPORT ").append(cType(method.getReturnType())).append(" JNICALL ")
                .append(jniSymbol(nativeClass, method, overloaded))
                .append("(JNIEnv *env, ")
                .append(Modifier.isStatic(method.getModifiers()) ? "jclass" : "jobject")
                .append(" self");
        Class<?>[] parameters = method.getParameterTypes();
        for (int index = 0; index < parameters.length; index++) {
            output.append(", ").append(cType(parameters[index])).append(" arg").append(index);
        }
        output.append(')');
    }

    private static String jniSymbol(Class<?> nativeClass, Method method, boolean overloaded) {
        StringBuilder symbol = new StringBuilder("Java_")
                .append(encode(nativeClass.getName().replace('.', '/')))
                .append('_').append(encode(method.getName()));
        if (overloaded) {
            String methodDescriptor = descriptor(method);
            symbol.append("__").append(encode(methodDescriptor.substring(1, methodDescriptor.indexOf(')'))));
        }
        return symbol.toString();
    }

    private static String descriptor(Method method) {
        StringBuilder descriptor = new StringBuilder("(");
        for (Class<?> parameter : method.getParameterTypes()) descriptor.append(descriptor(parameter));
        return descriptor.append(')').append(descriptor(method.getReturnType())).toString();
    }

    private static String descriptor(Class<?> type) {
        if (type.isArray()) return type.getName().replace('.', '/');
        if (!type.isPrimitive()) return "L" + type.getName().replace('.', '/') + ";";
        if (type == void.class) return "V";
        if (type == boolean.class) return "Z";
        if (type == byte.class) return "B";
        if (type == char.class) return "C";
        if (type == short.class) return "S";
        if (type == int.class) return "I";
        if (type == long.class) return "J";
        if (type == float.class) return "F";
        if (type == double.class) return "D";
        throw ExceptionHandling.invalid("Unsupported Java type: " + type);
    }

    private static String encode(String value) {
        StringBuilder encoded = new StringBuilder(value.length() + 8);
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character == '/') encoded.append('_');
            else if (character == '_') encoded.append("_1");
            else if (character == ';') encoded.append("_2");
            else if (character == '[') encoded.append("_3");
            else if (Character.isLetterOrDigit(character)) encoded.append(character);
            else encoded.append(String.format(Locale.ROOT, "_0%04x", (int) character));
        }
        return encoded.toString();
    }

    private static String cType(Class<?> type) {
        if (type == void.class) return "void";
        if (type == boolean.class) return "jboolean";
        if (type == byte.class) return "jbyte";
        if (type == char.class) return "jchar";
        if (type == short.class) return "jshort";
        if (type == int.class) return "jint";
        if (type == long.class) return "jlong";
        if (type == float.class) return "jfloat";
        if (type == double.class) return "jdouble";
        if (type == String.class) return "jstring";
        if (type.isArray()) {
            return switch (type.getComponentType().getName()) {
                case "boolean" -> "jbooleanArray";
                case "byte" -> "jbyteArray";
                case "char" -> "jcharArray";
                case "short" -> "jshortArray";
                case "int" -> "jintArray";
                case "long" -> "jlongArray";
                case "float" -> "jfloatArray";
                case "double" -> "jdoubleArray";
                default -> "jobjectArray";
            };
        }
        return "jobject";
    }

    private static void appendDefaultReturn(StringBuilder source, Class<?> type) {
        if (type == void.class) source.append("    return;\n");
        else if (type == boolean.class) source.append("    return JNI_FALSE;\n");
        else if (type.isPrimitive()) source.append("    return 0;\n");
        else source.append("    return NULL;\n");
    }

    private static String includeGuard(Class<?> nativeClass) {
        String value = nativeClass.getName().replaceAll("[^A-Za-z0-9]", "_").toUpperCase(Locale.ROOT);
        return "JFLUX_JNI_" + value + "_H";
    }
}
