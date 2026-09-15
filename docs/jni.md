# JNI package

`com.fortrow.jflux.jni` is a small, dependency-free foundation for Java-to-C
bridges. It handles the platform details that are easy to duplicate incorrectly
across applications while leaving the native implementation and toolchain
choices under the programmer's control.

## Platform names and library resources

`JNIPlatform` normalizes common JVM values such as `AMD64`, `x86_64`, and
`arm64`:

```java
JNIPlatform platform = JNI.platform();
String resource = platform.resourcePath("telemetry");
// Linux x86_64: native/linux/x86_64/libtelemetry.so
```

Bundle each native artifact under the corresponding path in the application's
resources:

```text
src/main/resources/native/
  linux/x86_64/libtelemetry.so
  macos/aarch64/libtelemetry.dylib
  windows/x86_64/telemetry.dll
```

The normalized resource identifiers are `windows`, `macos`, `linux`, and
`freebsd`; architecture identifiers include `x86_64`, `x86`, `aarch64`, `arm`,
`riscv64`, `ppc64le`, and `s390x`.

## Loading a library

`NativeLibraryLoader` first tries the system library search when enabled, then
falls back to the platform-specific classpath resource. Resources are copied
to an absolute temporary path because `System.load` cannot load directly from a
JAR. Extraction is bounded to 64 MiB by default and library names/resource roots
are validated to prevent path traversal.

```java
NativeLoadOptions options = NativeLoadOptions.defaults()
        .withPreferSystemLibrary(true)
        .withDeleteOnExit(true);

NativeLibraryLoadResult result = NativeLibraryLoader.load(
        "telemetry", MyNativeBridge.class, options);
```

Use `NativeLibraryLoader.extract` when an application needs to inspect or
manage the extracted file itself. Native loading remains process-global in the
JVM; the result reports whether the current call loaded a system/resource copy
or observed an already loaded platform key.

## Generating a header and starter implementation

Declare native methods in Java and generate declarations or a C stub:

```java
public final class NativeBridge {
    public static native int version();
    public native byte[] transform(byte[] input);
}

String header = JNI.header(NativeBridge.class);
String source = JNI.sourceTemplate(NativeBridge.class);
```

`JNIHeaderGenerator` maps primitive, `String`, primitive-array, and object
types to JNI C types. It also applies JNI name escaping and overload suffixes.
For production builds, `javac -h` remains a valid alternative when the build
system already owns header generation.

## Building without a shell

`JNIBuilder` creates an argument-list command and delegates execution to the
bounded `com.fortrow.jflux.shell.Shell` runner. It adds the current JDK's
`include` and platform JNI include directories automatically, emits `.dll`,
`.dylib`, or `.so` output names, and supports a selected compiler when the
application needs a specific toolchain.

```java
NativeBuildResult build = JNI.builder("telemetry")
        .sources(List.of(Path.of("native", "bridge.c")))
        .includeDirectory(Path.of("native", "include"))
        .define("TELEMETRY_VERSION=1")
        .outputDirectory(Path.of("build", "native"))
        .build();

if (!build.success()) {
    throw new IllegalStateException(build.commandResult().output());
}
```

Compiler discovery uses `cc`, `clang`, and `gcc` on Unix-like systems and
`cl`, `clang`, and `gcc` on Windows. A compiler can be selected explicitly with
`.compiler("clang")`. The builder passes every value as a separate process
argument; compiler flags must not contain shell pipelines, redirects, or
command chaining.

The reusable C starter is also available at
`com/fortrow/jflux/jni/jni_bridge_template.c` in the JFlux resources. It uses
only JNI APIs so operating-system-specific code can be isolated behind a
small native implementation layer when it is truly needed.

## Native boundary guidance

- Keep Java-facing native methods narrow and validate inputs before crossing the
  boundary.
- Check `ExceptionCheck`/`ExceptionOccurred` after JNI calls that can fail, and
  release UTF strings, arrays, and critical sections on every path.
- Do not retain local references or `JNIEnv*` values beyond their valid scope.
- Treat native library load failures as startup/feature-boundary failures; JFlux
  routes final load failures through its configured exception reporting path.
- Build one artifact per normalized OS/architecture pair and test loading on
  every target rather than inferring compatibility from the compiler exit code.
