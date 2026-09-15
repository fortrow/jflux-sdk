package com.fortrow.jflux.xql;

import com.fortrow.jflux.exceptions.types.CommonIOException;

/** Resolves changed rows to model IDs and queues rebuild work. */
@FunctionalInterface
public interface XqlRebuildHandler {
    void onChange(XqlChangeEvent event, XqlJobRepository jobs) throws CommonIOException, XqlException;
}
