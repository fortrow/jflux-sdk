# Output conversion

JFlux keeps JSON in the established `com.fortrow.jflux.output.json` namespace.
The `com.fortrow.jflux.output` package focuses on format conversion rather than
owning JSON or CSV APIs.

## JSON and XML conversion

`Converter` converts JSON-Simple objects to compact XML and XML DOM documents
back to JSON-Simple objects:

```java
Converter converter = new Converter();
String xml = converter.jsonToXml(JSON.getObject("{\"name\":\"Ada\"}"));
JSONObject json = converter.xmlToJson(new XML(file).getDocument());
```

JSON objects become child elements under `root` by default. Arrays and repeated
element names are represented by repeated XML elements and become JSON arrays
when read back. XML element names are validated before output is created.

The secure XML loader lives at `com.fortrow.jflux.xml.XML`. It disables DTD,
external entity, external schema, and XInclude access to prevent common XXE
risks. `com.fortrow.jflux.output.xml.XML` remains as a deprecated compatibility
subclass.
