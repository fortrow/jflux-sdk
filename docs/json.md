# JSON utilities

JFlux 1.1.0 uses JSON-Simple as the default representation for JSON data. The
primary facade is `com.fortrow.jflux.output.json.JSON`.

## Basic usage

```java
Object value = JSON.parse("{\"name\":\"Ada\",\"roles\":[\"admin\"]}");
String compact = JSON.string(value);
String readable = JSON.prettyString(value);
```

`parse(String)` returns JSON-Simple values: `JSONObject`, `JSONArray`, strings,
numbers, booleans, or `null`. Invalid or blank input raises a JFlux invalid-input
exception. `parse(String, Class<T>)` remains available for explicit Gson-backed
conversion into a Java type.

Use `JSON.isValid(String)` when a boolean result is more convenient than an
exception. It accepts any valid JSON value, including scalar values, and does
not report expected validation failures. `verify(String)` likewise keeps its
legacy sentinel behavior without logging malformed request data as an
application error.

## Compatibility helpers

The older convenience methods are retained for applications that use nullable
or sentinel-based parsing:

```java
JSONObject object = JSON.getObject(body); // empty object on invalid/non-object input
JSONArray array = JSON.getArray(body);    // empty array on invalid/non-array input
String payload = JSON.getString(object);
```

`getString(...)` produces compact JSON by default. Pass `true` to format the
result with indentation. `verify(String)` validates an object and returns its
compact form; the `output.json` facade returns `"false"` for invalid or
non-object input, matching the existing HTTP server contract.

The historical `com.fortrow.jflux.json.JSON` class delegates to the same
implementation and is deprecated for new code. Its `verify` method keeps its
legacy `null`-on-failure result so existing callers can migrate incrementally.

## Serialization behavior

JSON-Simple handles JSON-Simple values, maps, iterables, arrays, and primitive
values without constructing a new serializer for every call. Gson instances are
cached and retained only for explicit typed conversion, pretty formatting, and
fallback serialization of arbitrary Java objects.

For predictable wire formats, build a `JSONObject` or `JSONArray` (or use a
map/list) before serialization. JSON object key order is not guaranteed by
JSON-Simple, so consumers should compare parsed values rather than raw strings
when order is not part of the protocol.
