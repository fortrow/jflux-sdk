package com.fortrow.jflux.runtime;

/** Stable SPI implemented by built-in and dynamically loaded functions. */
public interface RuntimeFunction {
    String id();

    RuntimeResult execute(RuntimeContext context) throws Exception;
}
