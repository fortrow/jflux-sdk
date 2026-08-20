# Threading package

The `com.fortrow.jflux.threading` package provides small lifecycle helpers for
background work, sequential joined work, fan-out tasks, and bounded worker
queues.

## Thread lifecycle types

`DetachedThread` and `JoinedThread` retain their existing subclassing model:
override `execute()` and return `true` to continue looping or `false` to finish.
They now run on virtual threads and share lifecycle behavior through
`ThreadImpl`.

```java
DetachedThread worker = new DetachedThread() {
    @Override
    protected boolean execute() {
        doWork();
        return false;
    }
};

worker.start();
worker.await(Duration.ofSeconds(10));
```

The lifecycle is one-shot. `isStarted()`, `isActive()`, `isVirtual()`,
`getFailure()`, and `await(Duration)` make state observable without reaching
into the underlying `Thread`. Interrupting a worker requests cooperative
shutdown. Exceptions from `execute()`, `onStart()`, and `onStop()` are retained
as the failure and routed through the configured JFlux exception reporters.

Thread variables are backed by a concurrent map. Setting a variable to `null`
removes it, which preserves the old nullable behavior while avoiding null-value
concurrency errors.

## ThreadExecutor

`ThreadExecutor` is a fixed-size virtual-thread worker group with a bounded
queue. The existing `ThreadExecutor(int)` constructor uses a 1,024-item queue;
`ThreadExecutor(int workers, int queueCapacity)` allows applications to choose
the memory/backpressure limit. `execute()` rejects work after shutdown or when
the queue is full. `getQueueSize()`, `isClosed()`, and `awaitTermination()` are
available for lifecycle management. `close()` interrupts workers, waits briefly
for termination, and clears queued work.

Task failures are reported and do not silently kill the worker loop.

## ThreadFanout and ThreadGroup

`ThreadFanout` starts independent tasks concurrently, waits for all tasks, keeps
the first failure, and still allows the remaining tasks to finish. It rejects
new tasks after starting and clears worker references after completion.
`ThreadGroup` now honors its `containedThread` constructor argument, rejects
reuse after startup, uses virtual container threads, and interrupts all managed
threads during shutdown.

These helpers do not force a particular application shutdown policy. Work that
blocks indefinitely must still respond to interruption for prompt cleanup.
