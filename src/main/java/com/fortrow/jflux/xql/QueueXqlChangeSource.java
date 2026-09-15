package com.fortrow.jflux.xql;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/** In-process change source useful for adapters, tests, and application webhooks. */
public final class QueueXqlChangeSource implements XqlChangeSource {
    private final BlockingQueue<XqlChangeEvent> events = new LinkedBlockingQueue<>();
    private volatile boolean closed;
    public void publish(XqlChangeEvent event) { if (closed) throw new IllegalStateException("Change source is closed."); events.add(event); }
    @Override public XqlChangeEvent next() throws InterruptedException { return closed && events.isEmpty() ? null : events.take(); }
    @Override public void close() { closed = true; events.offer(new XqlChangeEvent("__closed__", XqlChangeEvent.Operation.DELETE, java.util.Map.of())); }
}
