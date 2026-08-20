/*
 * -------------------------------------------------------------------------
 * 
 * Copyright (c) 2026 Fortrow LLC. All rights reserved.
 *  
 * NOTICE: All information contained herein is, and remains the property of 
 * Fortrow, LLC. The intellectual and technical concepts contained herein are 
 * proprietary to [Company Name] and may be covered by U.S. and Foreign Patents, 
 * patents in process, and are protected by trade secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material is 
 * strictly forbidden unless prior written permission is obtained from 
 * Fortrow, LLC.
 * 
 * -------------------------------------------------------------------------
 * 
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-29 16:34:06 EDT
 * 
 * -------------------------------------------------------------------------

 */
package com.fortrow.jflux.console;

import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public final class ProgressTaskList implements AutoCloseable {
    private static final String ESC = "\033[";
    private static final String RESET = "\033[0m";
    private static final String GREEN = "\033[32m";
    private static final String RED = "\033[31m";
    private static final String DIM = "\033[2m";
    private static final String[] SPINNER = {"◢", "◣", "◤", "◥"};

    private final PrintStream out;
    private final ScheduledExecutorService renderer;
    private final List<TaskLine> tasks = Collections.synchronizedList(new ArrayList<>());
    private final ArrayList<Task> taskList = new ArrayList<>();
    private final AtomicBoolean rendering = new AtomicBoolean(false);
    private int frame = 0;
    private int renderedLines = 0;

    public ProgressTaskList() {
        this(System.out);
    }

    public ProgressTaskList(PrintStream out) {
        this.out = Objects.requireNonNull(out, "out");
        this.renderer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread thread = new Thread(r, "progress-task-list-renderer");
            thread.setDaemon(true);
            return thread;
        });
    }

    public Task append(String id, String label) {
        Objects.requireNonNull(label, "label");

        TaskLine line = new TaskLine(label);
        tasks.add(line);
        ensureRendering();
        Task t = new Task(id, line);
        taskList.add(t);
        return t;
    }

    public Task getTaskById(String id) {
        for(Task task : taskList) if(task.getId().equals(id)) return task;
        return null;
    }

    public boolean checkAllDone() {
        boolean allDone = true;
        for(Task task : taskList) if(!task.isDone()) allDone = false;
        return allDone;
    }

    public void completeRunningTasks() {
        finishRunningTasks(Status.SUCCESS);
    }

    public void failRunningTasks() {
        finishRunningTasks(Status.FAILURE);
    }

    public CompletableFuture<Void> allDone() {
        CompletableFuture<?>[] futures;
        synchronized (tasks) {
            futures = tasks.stream()
                    .map(task -> task.done)
                    .toArray(CompletableFuture[]::new);
        }
        return CompletableFuture.allOf(futures)
                .thenRun(this::render);
    }

    public void awaitCompletion() {
        allDone().join();
    }

    public void start(Duration interval) {
        Objects.requireNonNull(interval, "interval");
        long millis = Math.max(25L, interval.toMillis());
        if (rendering.compareAndSet(false, true)) {
            render();
            renderer.scheduleAtFixedRate(this::renderSafely, millis, millis, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void close() {
        renderer.shutdownNow();
        render();
        if (renderedLines > 0) {
            out.println();
        }
    }

    private void finishRunningTasks(Status status) {
        synchronized (tasks) {
            for (TaskLine task : tasks) {
                if (task.status.compareAndSet(Status.RUNNING, status)) {
                    task.done.complete(null);
                }
            }
        }
        render();
    }

    private void ensureRendering() {
        start(Duration.ofMillis(100));
    }

    private void renderSafely() {
        try {
            render();
        } catch (RuntimeException ignored) {
            // Rendering should never interrupt the application work.
        }
    }

    private synchronized void render() {
        if (renderedLines > 0) {
            out.print(ESC + renderedLines + "A" + "\r");
        }

        List<TaskLine> snapshot;
        synchronized (tasks) {
            snapshot = new ArrayList<>(tasks);
        }

        for (TaskLine task : snapshot) {
            out.print("\r" + ESC + "2K");
            out.println(format(task));
        }

        renderedLines = snapshot.size();
        frame = (frame + 1) % SPINNER.length;
        out.flush();
    }

    private String format(TaskLine task) {
        Status status = task.status.get();
        return switch (status) {
            case RUNNING -> DIM + SPINNER[frame] + RESET + " " + task.label;
            case SUCCESS -> GREEN + "✓" + RESET + " Completed: " + task.label;
            case FAILURE -> RED + "✗" + RESET + " Failed: " + task.label;
        };
    }

    private enum Status {
        RUNNING,
        SUCCESS,
        FAILURE
    }

    public final class Task {

        private final String id;
        private final TaskLine line;

        private Task(String id, TaskLine line) {
            this.id = id;
            this.line = line;
        }

        public String getId() {
            return id;
        }

        public void complete() {
            finish(Status.SUCCESS);
        }

        public void fail() {
            finish(Status.FAILURE);
        }

        public boolean isDone() {
            return line.status.get() != Status.RUNNING;
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
        private final String label;
        private final AtomicReference<Status> status = new AtomicReference<>(Status.RUNNING);
        private final CompletableFuture<Void> done = new CompletableFuture<>();

        private TaskLine(String label) {
            this.label = label;
        }
    }
}