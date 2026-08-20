package com.fortrow.jflux.output.json;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.LinkedHashMap;

import static org.junit.jupiter.api.Assertions.*;

class JSONTest {

    @Test
    void parsesAndSerializesJsonSimpleValuesByDefault() {
        Object parsed = JSON.parse("{\"name\":\"Ada\",\"values\":[1,2]}");
        assertInstanceOf(JSONObject.class, parsed);
        JSONObject object = (JSONObject) parsed;
        assertEquals("Ada", object.get("name"));
        JSONObject roundTrip = JSON.getObject(JSON.string(object));
        assertEquals("Ada", roundTrip.get("name"));
        assertEquals(List.of(1L, 2L), roundTrip.get("values"));
    }

    @Test
    void supportsArrayCompatibilityHelpers() {
        JSONArray array = JSON.getArray("[1,2,3]");
        assertEquals(3, array.size());
        assertEquals("[1,2,3]", JSON.getString(array));
        assertTrue(JSON.getArray("not-json").isEmpty());
    }

    @Test
    void validatesObjectsWithoutTheOldRegexParser() {
        assertEquals("{\"ok\":true}", JSON.verify(" { \"ok\" : true } "));
        assertEquals("false", JSON.verify("[1,2]"));
        assertFalse(JSON.isValid("{broken"));
    }

    @Test
    void retainsTypedGsonConversionOnlyForExplicitClassOverload() {
        Person person = JSON.parse("{\"name\":\"Ada\",\"age\":37}", Person.class);
        assertEquals("Ada", person.name);
        assertEquals(37, person.age);
        LinkedHashMap<String, Object> map = new LinkedHashMap<>();
        map.put("name", "Ada");
        map.put("age", 37);
        assertEquals("{\"name\":\"Ada\",\"age\":37}", JSON.string(map));
        assertTrue(JSON.prettyString(List.of("a", "b")).contains("\n"));
    }

    @Test
    void keepsHistoricalNamespaceAsACompatibilityFacade() {
        assertEquals("Ada", com.fortrow.jflux.json.JSON.getObject("{\"name\":\"Ada\"}").get("name"));
        assertNull(com.fortrow.jflux.json.JSON.verify("[1,2]"));
    }

    private static final class Person {
        private String name;
        private int age;
    }
}
