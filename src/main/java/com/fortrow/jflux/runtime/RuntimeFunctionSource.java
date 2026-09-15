package com.fortrow.jflux.runtime;

/** Trust and precedence class used when multiple functions have the same ID. */
public enum RuntimeFunctionSource {
    BUILTIN(100),
    LOCAL_PACKAGE(500),
    APPLICATION_OVERRIDE(700),
    SERVER_PACKAGE(800),
    SERVER_EPHEMERAL(1000);

    private final int priority;

    RuntimeFunctionSource(int priority) {
        this.priority = priority;
    }

    public int priority() {
        return priority;
    }
}
