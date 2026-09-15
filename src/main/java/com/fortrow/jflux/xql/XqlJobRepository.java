package com.fortrow.jflux.xql;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import java.util.List;

/** Durable queue contract for rebuild jobs. */
public interface XqlJobRepository {
    void enqueue(XqlJob job) throws CommonIOException;
    XqlJob claim() throws CommonIOException;
    void acknowledge(XqlJob job) throws CommonIOException;
    List<XqlJob> pending() throws CommonIOException;
}
