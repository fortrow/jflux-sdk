package com.fortrow.jflux.xql;

import com.fortrow.jflux.exceptions.types.CommonIOException;

/** Rebuild callback invoked for each claimed durable XQL job. */
@FunctionalInterface
public interface XqlJobProcessor {
    void process(XqlJob job) throws CommonIOException, XqlException;
}
