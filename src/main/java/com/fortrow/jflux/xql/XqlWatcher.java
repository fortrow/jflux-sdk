package com.fortrow.jflux.xql;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** Resumable virtual-thread watcher that turns change events into durable XQL jobs. */
public final class XqlWatcher implements AutoCloseable {
    private final XqlChangeSource source;
    private final XqlJobRepository jobs;
    private final XqlRebuildHandler handler;
    private final AtomicBoolean running = new AtomicBoolean();
    private Thread worker;

    public XqlWatcher(XqlChangeSource source, XqlJobRepository jobs, XqlRebuildHandler handler) {
        this.source = source; this.jobs = jobs; this.handler = handler;
    }

    /** Starts one watcher loop; calling start twice is harmless. */
    public synchronized void start() {
        if (!running.compareAndSet(false, true)) return;
        worker = Thread.ofVirtual().name("jflux-xql-watcher").start(this::run);
    }
    public boolean isRunning() { return running.get(); }
    public void await() throws InterruptedException { Thread thread = worker; if (thread != null) thread.join(); }
    private void run() {
        try {
            while (running.get()) {
                XqlChangeEvent event = source.next();
                if (event == null) break;
                if ("__closed__".equals(event.table())) break;
                handler.onChange(event, jobs);
            }
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        catch (CommonIOException | XqlException e) { e.report(); }
        finally { running.set(false); }
    }
    @Override public synchronized void close() { running.set(false); source.close(); Thread thread = worker; if (thread != null) thread.interrupt(); }
}
