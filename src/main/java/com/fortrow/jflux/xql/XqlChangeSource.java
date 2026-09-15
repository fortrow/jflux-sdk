package com.fortrow.jflux.xql;

import com.fortrow.jflux.exceptions.types.CommonIOException;

/** Streaming source boundary for MySQL binlog, CDC, or application-emitted changes. */
public interface XqlChangeSource extends AutoCloseable {
    XqlChangeEvent next() throws CommonIOException, InterruptedException;
    @Override void close();
}
