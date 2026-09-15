package com.fortrow.jflux.runtime;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/** Loaded runtime functions and the classloader that owns their bytecode. */
public final class RuntimePackage implements AutoCloseable {
    private final RuntimeFunctionDescriptor descriptor;
    private final List<RuntimeFunction> functions;
    private final RuntimeClassLoader loader;
    private final AtomicBoolean closed = new AtomicBoolean();

    RuntimePackage(RuntimeFunctionDescriptor descriptor, List<RuntimeFunction> functions,
                   RuntimeClassLoader loader) {
        this.descriptor = Objects.requireNonNull(descriptor, "descriptor");
        this.functions = List.copyOf(functions);
        this.loader = Objects.requireNonNull(loader, "loader");
    }

    public RuntimeFunctionDescriptor descriptor() { return descriptor; }

    public List<RuntimeFunction> functions() { return functions; }

    public boolean closed() { return closed.get(); }

    @Override
    public void close() {
        if(closed.compareAndSet(false, true)) loader.close();
    }
}
