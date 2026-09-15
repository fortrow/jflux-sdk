package com.fortrow.jflux.runtime;

import java.time.Duration;
import java.util.List;

/** Immutable request envelope passed to a dynamically loaded function. */
public record RuntimeRequest(String function, List<String> arguments, Duration timeout) {
    public RuntimeRequest {
        if(function == null || function.isBlank()) throw new IllegalArgumentException("function is required");
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        timeout = timeout == null ? Duration.ofMinutes(5) : timeout;
        if(timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
    }
}
