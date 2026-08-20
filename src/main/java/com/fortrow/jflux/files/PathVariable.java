package com.fortrow.jflux.files;

import com.fortrow.jflux.exceptions.ExceptionHandling;

/** A named placeholder used in a {@link PathObject}. */
public class PathVariable {

    private final String key;
    private String value;

    public PathVariable(String key) {
        if (key == null || key.isBlank()) throw ExceptionHandling.invalid("Variable key is required.");
        this.key = key;
    }

    public PathVariable(String key, String value) {
        this(key);
        this.value = value;
    }

    public void set(String value) {
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public boolean isset() {
        return value != null;
    }

    PathVariable copy() {
        return new PathVariable(key, value);
    }
}
