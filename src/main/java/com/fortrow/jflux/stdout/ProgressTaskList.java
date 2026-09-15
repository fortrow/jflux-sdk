/*
 * -------------------------------------------------------------------------
 *
 * Copyright (c) 2026 Fortrow LLC. All rights reserved.
 *
 * NOTICE: All information contained herein is, and remains the property of
 * Fortrow, LLC. The intellectual and technical concepts contained herein are
 * proprietary to Fortrow, LLC and may be covered by U.S. and Foreign Patents,
 * patents in process, and are protected by trade secret or copyright law.
 *
 * Dissemination of this information or reproduction of this material is
 * strictly forbidden unless prior written permission is obtained from
 * Fortrow, LLC.
 *
 * -------------------------------------------------------------------------
 */
package com.fortrow.jflux.stdout;

import com.fortrow.jflux.exceptions.ExceptionHandling;

import java.io.PrintStream;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lightweight, thread-safe terminal progress renderer.
 *
 * <p>Tasks can be completed from worker threads, labels can change while work
 * is running, and optional percentages are rendered without requiring a
 * second progress framework. The renderer owns one daemon scheduler and is
 * closed deterministically with the list.</p>
 */
public class ProgressTaskList implements AutoCloseable {

    private static final String ESC = "\033[";
    private static final String RESET = "\033[0m";
    private static final String GREEN = "\033[32m";
    private static final String RED = "\033[31m";
    private static final String YELLOW = "\033[33m";
    private static final String DIM = "\033[2m";
    private static final String[] SPINNER = {"◢", "◣", "◤", "◥"};

    private final PrintStream out;
    private final ScheduledExecutorService renderer;
    private final CopyOnWriteArrayList<TaskLine> tasks = new CopyOnWriteArrayList<>();
    private final ConcurrentHashMap<String, Task> taskById = new ConcurrentHashMap<>();
    private final AtomicBoolean rendering = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private final AtomicInteger generatedIds = new AtomicInteger();
    private int frame;
    private int renderedLines;

    public ProgressTaskList() {
        this(System.out);
    }

    public ProgressTaskList(PrintStream out) {
        if (out == null) throw ExceptionHandling.invalid("Progress output is required.");
        this.out = out;
        this.renderer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "jflux-progress-renderer");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** Adds a task with an application-supplied identifier. */
    public Task append(String id, String label) {
        ensureOpen();
        if (label == null || label.isBlank()) throw ExceptionHandling.invalid("Progress task label is required.");
        String taskId = id == null || id.isBlank() ? "task-" + generatedIds.incrementAndGet() : id;
        TaskLine line = new TaskLine(taskId, label);
        Task task = new Task(line);
        if (taskById.putIfAbsent(taskId, task) != null) {
            throw ExceptionHandling.invalid("A progress task with id '" + taskId + "' already exists.");
        }
        tasks.add(line);
        ensureRendering();
        return task;
    }

    /** Adds a task with a generated identifier. */
    public Task append(String label) {
        return append(null, label);
    }

    /** Finds a task by its identifier, or returns null when it does not exist. */
    public Task getTaskById(String id) {
        return id == null ? null : taskById.get(id);
    }

    /** Returns a stable snapshot of the currently registered tasks. */
    public List<Task> tasks() {
        return tasks.stream().map(task -> taskById.get(task.id)).filter(Objects::nonNull).toList();
    }

    public boolean checkAllDone() {
        for (TaskLine task : tasks) if (!task.isDone()) return false;
        return true;
    }

    public void completeRunningTasks() {
        finishRunningTasks(Status.SUCCESS);
    }

    public void failRunningTasks() {
        finishRunningTasks(Status.FAILURE);
    }

    public CompletableFuture<Void> allDone() {
        CompletableFuture<?>[] futures = tasks.stream().map(task -> task.done).toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(futures).thenRun(this::render);
    }

    public void awaitCompletion() {
        allDone().join();
    }

    /** Starts rendering at the requested interval; calls after the first are harmless. */
    public void start(Duration interval) {
        ensureOpen();
        if (interval == null || interval.isNegative() || interval.isZero()) {
            throw ExceptionHandling.invalid("Progress interval must be positive.");
        }
        long millis = Math.max(25L, interval.toMillis());
        if (rendering.compareAndSet(false, true)) {
            render();
            renderer.scheduleAtFixedRate(this::renderSafely, millis, millis, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        renderer.shutdownNow();
        render();
        if (renderedLines > 0) out.println();
    }

    private void finishRunningTasks(Status status) {
        for (TaskLine task : tasks) task.finish(status);
        render();
    }

    private void ensureRendering() {
        if (!rendering.get()) start(Duration.ofMillis(100));
    }

    private void ensureOpen() {
        if (closed.get()) throw ExceptionHandling.failure("Progress task list is closed.", null);
    }

    private void renderSafely() {
        try {
            render();
        } catch (RuntimeException ignored) {
            // A broken terminal must not interrupt application work.
        }
    }

    private synchronized void render() {
        if (renderedLines > 0) out.print(ESC + renderedLines + "A\r");
        for (TaskLine task : tasks) {
            out.print("\r" + ESC + "2K");
            out.println(format(task));
        }
        renderedLines = tasks.size();
        frame = (frame + 1) % SPINNER.length;
        out.flush();
    }

    private String format(TaskLine task) {
        Status status = task.status.get();
        String label = task.label.get();
        return switch (status) {
            case RUNNING -> DIM + SPINNER[frame] + RESET + " " + label + progress(task.progress.get());
            case SUCCESS -> GREEN + "✓" + RESET + " Completed: " + label;
            case FAILURE -> RED + "✗" + RESET + " Failed: " + label;
            case CANCELLED -> YELLOW + "!" + RESET + " Cancelled: " + label;
        };
    }

    private String progress(int value) {
        return value < 0 ? "" : " " + value + "%";
    }

    private enum Status {
        RUNNING,
        SUCCESS,
        FAILURE,
        CANCELLED
    }

    public final class Task {
        private final TaskLine line;

        private Task(TaskLine line) {
            this.line = line;
        }

        public String getId() {
            return line.id;
        }

        public String getLabel() {
            return line.label.get();
        }

        public Task updateLabel(String label) {
            if (label == null || label.isBlank()) throw ExceptionHandling.invalid("Progress task label is required.");
            line.label.set(label);
            render();
            return this;
        }

        public Task label(String label) {
            return updateLabel(label);
        }

        public Task progress(int percent) {
            if (percent < 0 || percent > 100) throw ExceptionHandling.invalid("Progress must be between 0 and 100.");
            line.progress.set(percent);
            render();
            return this;
        }

        public int getProgress() {
            return line.progress.get();
        }

        public void complete() {
            finish(Status.SUCCESS);
        }

        public void fail() {
            finish(Status.FAILURE);
        }

        public void fail(Throwable failure) {
            if (failure != null) ExceptionHandling.report(failure);
            finish(Status.FAILURE);
        }

        public void cancel() {
            finish(Status.CANCELLED);
        }

        public boolean isDone() {
            return line.isDone();
        }

        public boolean isSuccessful() {
            return line.status.get() == Status.SUCCESS;
        }

        public boolean isFailed() {
            return line.status.get() == Status.FAILURE;
        }

        public boolean isCancelled() {
            return line.status.get() == Status.CANCELLED;
        }

        public CompletableFuture<Void> done() {
            return line.done;
        }

        private void finish(Status status) {
            if (line.status.compareAndSet(Status.RUNNING, status)) {
                line.done.complete(null);
                render();
            }
        }
    }

    private static final class TaskLine {
        private final String id;
        private final AtomicReference<String> label;
        private final AtomicReference<Status> status = new AtomicReference<>(Status.RUNNING);
        private final AtomicInteger progress = new AtomicInteger(-1);
        private final CompletableFuture<Void> done = new CompletableFuture<>();

        private TaskLine(String id, String label) {
            this.id = Objects.requireNonNull(id, "id");
            this.label = new AtomicReference<>(label);
        }

        private boolean isDone() {
            return status.get() != Status.RUNNING;
        }

        private void finish(Status next) {
            if (status.compareAndSet(Status.RUNNING, next)) done.complete(null);
        }
    }
}
