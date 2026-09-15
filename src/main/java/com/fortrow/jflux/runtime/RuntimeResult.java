package com.fortrow.jflux.runtime;

/** Result returned by every dynamically invoked function. */
public record RuntimeResult(int exitCode, String output, String error) {
    public static RuntimeResult success(String output) {
        return new RuntimeResult(0, output == null ? "" : output, "");
    }

    public static RuntimeResult failure(String error) {
        return new RuntimeResult(1, "", error == null ? "Function failed" : error);
    }

    public boolean successful() {
        return exitCode == 0;
    }
}
