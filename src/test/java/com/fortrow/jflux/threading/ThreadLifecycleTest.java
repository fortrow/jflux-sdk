package com.fortrow.jflux.threading;

import com.fortrow.jflux.threading.types.DetachedThread;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class ThreadLifecycleTest {

    @Test
    void detachedThreadsUseVirtualThreadsAndCanBeAwaited() throws InterruptedException {
        DetachedThread thread = new DetachedThread() {
            @Override
            protected boolean execute() {
                return false;
            }
        };

        thread.start();

        assertTrue(thread.await(Duration.ofSeconds(2)));
        assertTrue(thread.isStarted());
        assertTrue(thread.isVirtual());
        assertFalse(thread.isActive());
        assertNull(thread.getFailure());
    }

    @Test
    void threadFailuresAreRetainedAndDoNotEscapeTheWorker() throws InterruptedException {
        DetachedThread thread = new DetachedThread() {
            @Override
            protected boolean execute() {
                throw new IllegalStateException("expected failure");
            }
        };

        thread.start();

        assertTrue(thread.await(Duration.ofSeconds(2)));
        assertNotNull(thread.getFailure());
        assertEquals("expected failure", thread.getFailure().getMessage());
    }
}
