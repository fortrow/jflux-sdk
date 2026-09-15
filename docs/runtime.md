# Runtime

JFlux 1.1.0 provides a library-oriented dynamic runtime adapted from the Fortrow CLI execution design. The JFlux runtime is independent of the CLI project and exposes its own stable SPI under `com.fortrow.jflux.runtime`.

## Define a function

```java
public final class HelloFunction implements RuntimeFunction {
    public String id() {
        return "hello";
    }

    public RuntimeResult execute(RuntimeContext context) {
        return RuntimeResult.success("hello " + context.request().arguments().getFirst());
    }
}
```

Register built-ins and execute them through `DynamicRuntime`:

```java
try (DynamicRuntime runtime = new DynamicRuntime()) {
    runtime.registerBuiltin(new HelloFunction());
    RuntimeResult result = runtime.execute(new RuntimeRequest(
            "hello", List.of("world"), Duration.ofSeconds(5)));
}
```

Requests are immutable. Arguments are copied, timeouts must be positive, and execution runs on Java virtual threads. A timeout cancels the task and returns a failed `RuntimeResult`.

## Loading a function JAR

A runtime JAR contains:

- `META-INF/jflux-runtime.properties`
- `META-INF/services/com.fortrow.jflux.runtime.RuntimeFunction`
- one or more public `RuntimeFunction` implementations

The manifest supports `id`, `packageId`, `version`, `replaces`, `expiresAt`, and `override`. Service providers are instantiated through a parent-first in-memory classloader.

```java
try (DynamicRuntime runtime = new DynamicRuntime()) {
    runtime.load(Path.of("function.jar"), RuntimeFunctionSource.LOCAL_PACKAGE);
}
```

JAR entries are expanded with bounded limits, and the loader stores class and resource bytes in memory rather than leaving an open `JarFile`. Closing the returned `RuntimePackage` releases its byte arrays and prevents further class discovery.

## Encrypted packages

`EncryptedRuntimePackage` provides a versioned AES-GCM envelope. The package key is supplied by a callback and is never stored in the package:

```java
runtime.loadEncrypted(encoded, (keyId, packageId, version) -> key,
        RuntimeFunctionSource.SERVER_EPHEMERAL);
```

Decrypted JAR bytes are held only for loading and are wiped after the classloader has copied the entries. The authenticated header covers the package format, key ID, manifest, and nonce.

## Function precedence

Functions are selected by source priority, then version. Built-ins can explicitly opt into server replacement. A package must declare replacement targets and `override=true`; unauthorized collisions are rejected.

## Compatibility

The old `com.fortrow.jflux.runtime.lib` stream classes remain as deprecated compatibility helpers because existing STRATO consumers reference them. New code should use `DynamicRuntime`, `RuntimePackageLoader`, and `RuntimeClassLoader`.
