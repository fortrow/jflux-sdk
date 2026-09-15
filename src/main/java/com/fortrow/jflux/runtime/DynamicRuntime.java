package com.fortrow.jflux.runtime;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Library facade for built-ins, encrypted packages, and bounded dynamic execution. */
public final class DynamicRuntime implements AutoCloseable {
    private final RuntimeFunctionRegistry registry;
    private final RuntimePackageLoader loader;

    public DynamicRuntime() {
        this(new RuntimeFunctionRegistry(), new RuntimePackageLoader());
    }

    public DynamicRuntime(RuntimeFunctionRegistry registry, RuntimePackageLoader loader) {
        this.registry = registry == null ? new RuntimeFunctionRegistry() : registry;
        this.loader = loader == null ? new RuntimePackageLoader() : loader;
    }

    public RuntimeFunctionRegistry registry() { return registry; }

    public void registerBuiltin(RuntimeFunction function) { registry.registerBuiltin(function); }

    public void registerBuiltin(RuntimeFunction function, boolean serverOverrideable) {
        registry.registerBuiltin(function, serverOverrideable);
    }

    public void registerApplicationOverride(RuntimeFunction function, String version, List<String> replaces) {
        registry.registerApplicationOverride(function, version, replaces);
    }

    public RuntimePackage load(byte[] jar, RuntimeFunctionSource source) throws IOException {
        RuntimePackage runtimePackage = loader.load(jar, source);
        registry.registerPackage(runtimePackage);
        return runtimePackage;
    }

    public RuntimePackage load(Path jar, RuntimeFunctionSource source) throws IOException {
        RuntimePackage runtimePackage = loader.load(jar, source);
        registry.registerPackage(runtimePackage);
        return runtimePackage;
    }

    public RuntimePackage loadEncrypted(byte[] encoded, RuntimePackageKeyProvider keys,
                                        RuntimeFunctionSource source) throws Exception {
        RuntimePackage runtimePackage = loader.loadEncrypted(encoded, keys, source);
        registry.registerPackage(runtimePackage);
        return runtimePackage;
    }

    public RuntimeResult execute(RuntimeRequest request) { return registry.execute(request); }

    @Override
    public void close() { registry.close(); }
}
