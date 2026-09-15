package com.fortrow.jflux.output;

import com.fortrow.jflux.csv.Csv;
import com.fortrow.jflux.xml.XML;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class OutputCsvTest {

    @Test
    void convertsJsonSimpleObjectsToXmlAndBack() {
        JSONObject input = new JSONObject();
        input.put("name", "Ada");
        input.put("values", List.of("one", "two"));

        Converter converter = new Converter();
        String xml = converter.jsonToXml(input);
        assertTrue(xml.contains("<name>Ada</name>"));
        JSONObject output = converter.xmlToJson(parse(xml));
        assertEquals("Ada", ((JSONObject) output.get("root")).get("name"));
    }

    @Test
    void csvConvenienceHelpersCreateDirectoriesAndCloseResources(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("nested/report.csv");
        Csv.writeAll(file, List.of(List.of("name", "score"), List.of("Ada", "42")));

        assertEquals(List.of(List.of("name", "score"), List.of("Ada", "42")), Csv.readAll(file));
    }

    @Test
    void xmlLoaderRejectsExternalEntityDeclarations(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("unsafe.xml");
        Files.writeString(file, "<!DOCTYPE root [<!ENTITY xxe SYSTEM 'file:///etc/passwd'>]><root>&xxe;</root>");

        assertThrows(IllegalStateException.class, () -> new XML(file.toFile()));
    }

    private static org.w3c.dom.Document parse(String xml) {
        try {
            Path file = Files.createTempFile("jflux-output-test", ".xml");
            Files.writeString(file, xml);
            try {
                return new XML(file.toFile()).getDocument();
            } finally {
                Files.deleteIfExists(file);
            }
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
