package com.fortrow.jflux.xql;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class XqlModelTest {
    @Test void writesEmbeddedDataAndStaticReferences() throws Throwable {
        Event event = new Event();
        event.id("evt-9");
        RaceResult result = new RaceResult(event);
        String xml = result.toXml();
        assertEquals("race_result", result.name());
        assertEquals("race_results/race-7.xml", result.path());
        assertTrue(xml.contains("<driver>Ada &amp; Bob</driver>"));
        assertTrue(xml.replaceAll("\\s+", "").contains("<tags><tag>wet</tag><tag>night</tag></tags>"));
        assertTrue(xml.contains("xql_path=\"events/evt-9.xml\""));
        assertFalse(xml.contains("<title>Championship</title>"));
    }

    @Test void localStorageRoundTripAndFinalProtection() throws Throwable {
        Path root = Files.createTempDirectory("jflux-xql-");
        try {
            LocalXqlStorage storage = new LocalXqlStorage(root);
            FinalNote note = new FinalNote();
            note.persist(storage);
            assertTrue(note.fetch(storage).contains("<text>reviewed</text>"));
            assertThrows(XqlException.class, () -> note.persist(storage));
        } finally { com.fortrow.jflux.files.Files.rmTree(root.toString()); }
    }

    @Test void requiredFieldsAndUnsafeObjectKeysAreRejected() throws Throwable {
        assertThrows(XqlException.class, () -> new Required().toXml());
        Path root = Files.createTempDirectory("jflux-xql-");
        try { assertThrows(Throwable.class, () -> new LocalXqlStorage(root).put("../outside.xml", "bad")); }
        finally { com.fortrow.jflux.files.Files.rmTree(root.toString()); }
    }

    @Test void hydrationMigrationBindingsJobsIndexAndWatcherWork() throws Throwable {
        Event event = new Event(); event.id("evt-9");
        RaceResult original = new RaceResult(event);
        String xml = original.toXml(false);
        RaceResult restored = new RaceResult(new Event());
        restored.hydrate(xml);
        assertEquals("Ada & Bob", restored.value("driver"));
        assertEquals(java.util.List.of("wet", "night"), restored.value("tag"));

        String migrated = restored.migrate("<root><old>one</old></root>", java.util.List.of(
                XqlMigration.rename("root/old", "root/new"), XqlMigration.add("root/added", "x")));
        assertTrue(migrated.contains("<new>one</new>") && migrated.contains("<added>x</added>"));

        Path root = Files.createTempDirectory("jflux-xql-");
        try {
            FileXqlJobRepository jobs = new FileXqlJobRepository(root.resolve("jobs.log"));
            jobs.enqueue(XqlJob.now("race_result", "race-7", "update"));
            assertEquals("race-7", jobs.claim().id());
            XqlSearchIndex index = new XqlSearchIndex(root.resolve("index.log"));
            index.index("race_result", "race-7", "driver", "Ada & Bob");
            assertEquals(1, index.search("Ada & Bob").size());

            QueueXqlChangeSource source = new QueueXqlChangeSource();
            XqlWatcher watcher = new XqlWatcher(source, jobs, (change, queue) -> queue.enqueue(XqlJob.now("race_result", String.valueOf(change.keys().get("id")), "binlog")));
            watcher.start(); source.publish(new XqlChangeEvent("race_results", XqlChangeEvent.Operation.UPDATE, java.util.Map.of("id", "race-8")));
            Thread.sleep(100); watcher.close(); watcher.await();
            assertEquals("race-8", jobs.claim().id());

            jobs.enqueue(XqlJob.now("race_result", "race-9", "manual"));
            java.util.concurrent.atomic.AtomicReference<String> processed = new java.util.concurrent.atomic.AtomicReference<>();
            XqlRebuilder rebuilder = new XqlRebuilder(jobs, job -> processed.set(job.id()));
            rebuilder.start(); Thread.sleep(100); rebuilder.close(); rebuilder.await();
            assertEquals("race-9", processed.get());

            BindingModel bound = new BindingModel();
            bound.refresh((table, columns, equals) -> java.util.List.of(java.util.Map.of("id", "db-1", "status", "published")));
            assertEquals("db-1", bound.value("id"));
            assertEquals("published", bound.value("status"));
        } finally { com.fortrow.jflux.files.Files.rmTree(root.toString()); }
    }

    private static final class Event extends XqlModel {
        @Override protected void schema(XqlModel model) { staticModel(); field("title").value("Championship").required(); }
    }
    private static final class RaceResult extends XqlModel {
        RaceResult(Event event) { super(); attach(event); }
        @Override protected void schema(XqlModel model) {
            primary(field("id").value("race-7"));
            field("driver").value("Ada & Bob").required();
            field("tag").add("wet").add("night");
        }
    }
    private static final class FinalNote extends XqlModel {
        @Override protected void schema(XqlModel model) { finalModel(); field("text").value("reviewed"); }
    }
    private static final class Required extends XqlModel {
        @Override protected void schema(XqlModel model) { field("value").required(); }
    }
    private static final class BindingModel extends XqlModel {
        @Override protected void schema(XqlModel model) {
            field("id"); field("status");
            bind(XqlBinding.table("race_results").columns("id", "status").where(java.util.Map.of("id", "db-1")).build());
        }
    }
}
