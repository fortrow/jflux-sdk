package com.fortrow.jflux.xql;

/** Durable unit of work produced by a database change or manual rebuild request. */
public record XqlJob(String model, String id, String reason, long createdAt) {
    public XqlJob {
        if (model == null || model.isBlank() || id == null || id.isBlank()) throw new IllegalArgumentException("Model and id are required.");
        reason = reason == null ? "change" : reason;
    }
    public static XqlJob now(String model, String id, String reason) { return new XqlJob(model, id, reason, System.currentTimeMillis()); }
}
