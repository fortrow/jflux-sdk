package com.fortrow.jflux.yaml;

import com.fortrow.jflux.files.FileObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YAMLTest {

    @Test
    void parsesMapsListsAndMultipleDocuments() {
        Map<String, Object> map = YAML.map("name: Ada\nroles:\n  - admin\n  - reviewer\n");
        assertEquals("Ada", map.get("name"));
        assertEquals(List.of("admin", "reviewer"), map.get("roles"));

        assertEquals(List.of("one", "two"), YAML.parseAll("---\none\n---\ntwo\n"));
        assertTrue(YAML.jsonString("name: Ada").contains("\"name\""));
    }

    @Test
    void convertsToTypesAndDumpsWithoutRebuildingSerializers() {
        Settings settings = YAML.parse("name: Ada\nport: 8443\n", Settings.class);
        assertEquals("Ada", settings.name);
        assertEquals(8443, settings.port);

        String yaml = YAML.string(Map.of("name", "Ada", "enabled", true));
        assertTrue(yaml.contains("name"));
        assertTrue(YAML.prettyJsonString("name: Ada").contains("\n"));
    }

    @Test
    void streamsAndFilesUseBoundedInput(@TempDir Path directory) throws Throwable {
        byte[] input = "name: Ada\n".getBytes(StandardCharsets.UTF_8);
        ByteArrayInputStream stream = new ByteArrayInputStream(input);
        assertEquals("Ada", ((Map<?, ?>) YAML.parseStream(stream)).get("name"));
        assertEquals(-1, stream.read());

        Path file = directory.resolve("config.yaml");
        Files.write(file, input);
        FileObject object = new FileObject(file.toFile());
        assertEquals("Ada", ((Map<?, ?>) YAML.parse(object)).get("name"));
        assertEquals("Ada", ((Map<?, ?>) YAML.parseFile(file.toString())).get("name"));

        assertThrows(IllegalArgumentException.class,
                () -> YAML.parseStream(new ByteArrayInputStream(input), 4));
    }

    @Test
    void rejectsInvalidInputAndLimits() throws Throwable {
        assertThrows(IllegalArgumentException.class, () -> YAML.parse((String) null));
        assertThrows(IllegalArgumentException.class, () -> YAML.parse("name: Ada", 0));
        assertThrows(IllegalArgumentException.class, () -> YAML.parse("name: Ada\n", 4));
        assertThrows(IllegalArgumentException.class, () -> YAML.map("- one"));
    }

    static final class Settings {
        String name;
        int port;
    }
}
