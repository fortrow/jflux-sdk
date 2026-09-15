package com.fortrow.jflux.xql;

import com.fortrow.jflux.exceptions.types.CommonIOException;

/** Durable object store used by XQL model instances. */
public interface XqlStorage {
    void put(String key, String content) throws CommonIOException;

    String get(String key) throws CommonIOException;
}
