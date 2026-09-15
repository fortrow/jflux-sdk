package com.fortrow.jflux.xql;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Base64;

/** Crash-tolerant append-only queue backed by a UTF-8 file. */
public final class FileXqlJobRepository implements XqlJobRepository {
    private final Path file;
    private final Object lock = new Object();
    public FileXqlJobRepository(Path file) { this.file = file.toAbsolutePath().normalize(); }

    @Override public void enqueue(XqlJob job) throws CommonIOException {
        synchronized (lock) { List<XqlJob> jobs = read(); jobs.add(job); write(jobs); }
    }
    @Override public XqlJob claim() throws CommonIOException {
        synchronized (lock) { List<XqlJob> jobs = read(); if (jobs.isEmpty()) return null; XqlJob job = jobs.getFirst(); write(jobs.subList(1, jobs.size())); return job; }
    }
    @Override public void acknowledge(XqlJob job) { /* claim removes the durable queue entry; acknowledgement is an explicit lifecycle hook. */ }
    @Override public List<XqlJob> pending() throws CommonIOException { synchronized (lock) { return List.copyOf(read()); } }

    private List<XqlJob> read() throws CommonIOException {
        try {
            if (!Files.exists(file)) return new ArrayList<>();
            List<XqlJob> result = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                String[] fields = line.split("\\t", -1); if (fields.length != 4) continue;
                result.add(new XqlJob(decode(fields[0]), decode(fields[1]), decode(fields[2]), Long.parseLong(fields[3])));
            }
            return result;
        } catch (Exception e) { throw new CommonIOException("Unable to read XQL job queue.", e); }
    }
    private void write(List<XqlJob> jobs) throws CommonIOException {
        try { Files.createDirectories(file.getParent()); StringBuilder out = new StringBuilder(); for (XqlJob job : jobs) out.append(encode(job.model())).append('\t').append(encode(job.id())).append('\t').append(encode(job.reason())).append('\t').append(job.createdAt()).append('\n'); Files.writeString(file, out, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING); }
        catch (IOException e) { throw new CommonIOException("Unable to write XQL job queue.", e); }
    }
    private static String encode(String value) { return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8)); }
    private static String decode(String value) { return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8); }
}
