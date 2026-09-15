package com.fortrow.jflux.xql;

import com.fortrow.jflux.exceptions.types.CommonIOException;
import com.fortrow.jflux.todash.__;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import org.xml.sax.InputSource;

/**
 * Declarative XML persistence model. Embedded child models serialize inline;
 * static child models persist independently and serialize as XQL references.
 */
public abstract class XqlModel extends XqlObject {
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private String id;
    private String primaryField;
    private boolean staticModel;
    private boolean finalModel;
    private boolean persisted;
    private final List<XqlBinding> bindings = new ArrayList<>();
    private final List<XqlMigration> migrations = new ArrayList<>();

    protected XqlModel() { this(null); }

    protected XqlModel(String name) {
        super(name == null ? "xql_model" : name);
        if (name == null) name(modelNameFor(getClass()));
        schema(this);
    }

    /** Define fields, groups, child models, and the primary field. */
    protected abstract void schema(XqlModel model);

    @Override
    public XqlModel required() { super.required(); return this; }

    @Override
    public XqlModel multiple() { super.multiple(); return this; }

    protected final <T extends XqlModel> T attach(T model) {
        if (model == null) throw new IllegalArgumentException("Attached model is required.");
        addChild(model);
        return model;
    }

    protected final void primary(XqlField field) { primary(field.name()); }
    protected final void primary(String fieldName) { this.primaryField = fieldName; }
    protected final void staticModel() { this.staticModel = true; }
    protected final void finalModel() { this.finalModel = true; }
    /** Registers a database binding used when rebuilding this model. */
    protected final XqlBinding bind(XqlBinding binding) { if (binding == null) throw new IllegalArgumentException("Binding is required."); bindings.add(binding); return binding; }
    public final List<XqlBinding> bindings() { return List.copyOf(bindings); }
    protected final void migration(XqlMigration migration) { if (migration == null) throw new IllegalArgumentException("Migration is required."); migrations.add(migration); }
    public final List<XqlMigration> migrations() { return List.copyOf(migrations); }

    /** Executes all registered bindings and fills matching fields from their first row. */
    public final XqlModel refresh(XqlDataSource source) throws CommonIOException, XqlException {
        for (XqlBinding binding : bindings) binding.fill(this, source);
        return this;
    }

    public final XqlModel attribute(String name, String value) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Attribute name is required.");
        attributes.put(name, value == null ? "" : value);
        return this;
    }

    public final String id() {
        if (id != null) return id;
        XqlField field = findField(this, primaryField);
        if (field != null && field.value() != null && !field.value().isBlank()) return id = field.value();
        return id = UUID.randomUUID().toString();
    }

    public final XqlModel id(String id) { this.id = nonBlank(id, "Model id is required."); return this; }
    public final boolean isStatic() { return staticModel; }
    public final boolean isFinal() { return finalModel; }
    public final String path() { return plural(name()) + "/" + id() + ".xml"; }

    public final String toXml() throws XqlException { return toXml(true); }

    public final String toXml(boolean formatted) throws XqlException {
        validate(this);
        try {
            Document document = document();
            Element root = document.createElement(name());
            document.appendChild(root);
            for (Map.Entry<String, String> attribute : attributes.entrySet()) root.setAttribute(attribute.getKey(), attribute.getValue());
            for (Object child : children()) append(document, root, child);
            return serialize(document, formatted);
        } catch (Exception e) {
            throw new XqlException("Unable to generate XQL XML.", e);
        }
    }

    public final String schemaSignature() throws XqlException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return java.util.HexFormat.of().formatHex(digest.digest(schemaShape(this).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new XqlException("Unable to create XQL schema signature.", e); }
    }

    public final void persist(XqlStorage storage) throws CommonIOException, XqlException {
        if (finalModel && persisted) throw new XqlException("Final XQL model cannot be modified after persistence.");
        storage.put(path(), toXml());
        persisted = true;
    }

    /** Reads and securely validates a stored XML document for this model's path. */
    public final String fetch(XqlStorage storage) throws CommonIOException, XqlException {
        String xml = storage.get(path());
        hydrate(xml);
        return xml;
    }

    /** Fetches and hydrates this model, including embedded children. */
    public final XqlModel fetchAndHydrate(XqlStorage storage) throws CommonIOException, XqlException {
        hydrate(storage.get(path()));
        return this;
    }

    /** Populates declared fields and embedded models from a secure XML document. */
    public final XqlModel hydrate(String xml) throws XqlException {
        if (xml == null || xml.isBlank()) throw new XqlException("XQL XML content is required.");
        try {
            Document parsed = parser().parse(new InputSource(new StringReader(xml)));
            if (!parsed.getDocumentElement().getTagName().equals(name()))
                throw new XqlException("Unexpected XQL root element: " + parsed.getDocumentElement().getTagName());
            hydrateObject(this, parsed.getDocumentElement());
            String sourceId = parsed.getDocumentElement().getAttribute("xql_id");
            if (!sourceId.isBlank()) id = sourceId;
            persisted = true;
            return this;
        } catch (XqlException e) { throw e; }
        catch (Exception e) { throw new XqlException("Stored XQL XML is invalid.", e); }
    }

    /** Populates matching declared fields from a database row or API payload. */
    public final XqlModel fill(Map<String, ?> values) throws XqlException {
        if (values == null) return this;
        fillObject(this, values);
        return this;
    }

    /** Returns a declared field value by name, or null when it is absent. */
    public final Object value(String fieldName) {
        XqlField field = findField(this, fieldName);
        if (field == null) return null;
        return field.isMultiple() ? field.values() : field.value();
    }

    private static void fillObject(XqlObject object, Map<String, ?> values) {
        for (Object child : object.children()) {
            if (child instanceof XqlField field && values.containsKey(field.name())) {
                Object value = values.get(field.name());
                if (value instanceof Iterable<?> iterable) {
                    field.multiple();
                    field.replaceValues(java.util.stream.StreamSupport.stream(iterable.spliterator(), false).filter(java.util.Objects::nonNull).map(String::valueOf).toList());
                } else field.value(value);
            } else if (child instanceof XqlObject nested) fillObject(nested, values);
        }
    }

    /** Applies ordered XML migrations and returns the migrated document. */
    public final String migrate(String xml, List<XqlMigration> migrations) throws XqlException {
        if (xml == null || migrations == null || migrations.isEmpty()) return xml;
        try {
            Document parsed = parser().parse(new InputSource(new StringReader(xml)));
            for (XqlMigration migration : migrations) migration.apply(parsed);
            return serialize(parsed, true);
        } catch (XqlException e) { throw e; }
        catch (Exception e) { throw new XqlException("Unable to migrate XQL XML.", e); }
    }

    /** Applies migrations registered by this model's schema definition. */
    public final String migrate(String xml) throws XqlException { return migrate(xml, migrations); }

    private static void hydrateObject(XqlObject object, Element element) throws XqlException {
        for (Object child : object.children()) {
            if (child instanceof XqlField field) {
                List<String> values = new ArrayList<>();
                if (field.isMultiple()) {
                    Element wrapper = firstChild(element, plural(field.name()));
                    if (wrapper != null) for (Element value : childElements(wrapper, field.name())) values.add(value.getTextContent());
                } else {
                    Element value = firstChild(element, field.name());
                    if (value != null) values.add(value.getTextContent());
                }
                if (field.isRequired() && values.isEmpty()) throw new XqlException(field.name() + " is required.");
                field.replaceValues(values);
            } else if (child instanceof XqlModel model) {
                Element childElement = firstChild(element, model.name());
                if (childElement != null && !model.isStatic()) hydrateObject(model, childElement);
                if (childElement != null && model.isStatic()) {
                    String referenceId = childElement.getAttribute("xql_id");
                    if (!referenceId.isBlank()) model.id(referenceId);
                }
            } else if (child instanceof XqlObject nested) {
                Element nestedElement = firstChild(element, nested.name());
                if (nestedElement != null) hydrateObject(nested, nestedElement);
            }
        }
    }

    private static Element firstChild(Element parent, String name) {
        for (Element element : childElements(parent, null)) if (element.getTagName().equals(name)) return element;
        return null;
    }

    private static List<Element> childElements(Element parent, String name) {
        List<Element> result = new ArrayList<>();
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node instanceof Element element && (name == null || element.getTagName().equals(name))) result.add(element);
        }
        return result;
    }

    private static void append(Document document, Element parent, Object child) throws XqlException {
        if (child instanceof XqlField field) {
            if (field.isMultiple()) {
                Element wrapper = document.createElement(plural(field.name()));
                parent.appendChild(wrapper);
                for (String value : field.values()) element(document, wrapper, field.name(), value);
            } else element(document, parent, field.name(), field.value());
            return;
        }
        if (!(child instanceof XqlObject object)) throw new XqlException("Unknown XQL child type.");
        if (object instanceof XqlModel model && model.isStatic()) {
            Element reference = document.createElement(object.name());
            reference.setAttribute("xql_model", object.name());
            reference.setAttribute("xql_id", model.id());
            reference.setAttribute("xql_path", model.path());
            parent.appendChild(reference);
            return;
        }
        Element element = document.createElement(object.name());
        parent.appendChild(element);
        for (Object nested : object.children()) append(document, element, nested);
    }

    private static void element(Document document, Element parent, String name, String value) {
        Element element = document.createElement(name);
        if (value != null) element.appendChild(document.createTextNode(value));
        parent.appendChild(element);
    }

    private static String serialize(Document document, boolean formatted) throws Exception {
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no");
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, formatted ? "yes" : "no");
        if (formatted) transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        StringWriter output = new StringWriter();
        transformer.transform(new DOMSource(document), new StreamResult(output));
        return output.toString();
    }

    private static void validate(XqlObject object) throws XqlException {
        for (Object child : object.children()) {
            if (child instanceof XqlField field) {
                if (field.isRequired() && field.values().isEmpty()) throw new XqlException(field.name() + " is required.");
            } else if (child instanceof XqlObject nested) {
                if (nested.isRequired() && nested.children().isEmpty()) throw new XqlException(nested.name() + " is required.");
                validate(nested);
            }
        }
    }

    private static XqlField findField(XqlObject object, String fieldName) {
        if (fieldName == null) return null;
        for (Object child : object.children()) {
            if (child instanceof XqlField field && field.name().equals(fieldName)) return field;
            if (child instanceof XqlObject nested) {
                XqlField found = findField(nested, fieldName);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static String schemaShape(XqlObject object) {
        StringBuilder result = new StringBuilder(object.name()).append('[');
        for (Object child : object.children()) {
            if (child instanceof XqlField field) result.append("f:").append(field.name()).append(':').append(field.isMultiple()).append(':').append(field.isRequired());
            else if (child instanceof XqlModel nested) result.append("m:").append(nested.name()).append(':').append(nested.isStatic()).append(':').append(nested.isFinal()).append(schemaShape(nested));
            else if (child instanceof XqlObject nested) result.append("o:").append(schemaShape(nested));
            result.append(';');
        }
        return result.append(']').toString();
    }

    private static Document document() throws Exception {
        return parser().newDocument();
    }

    private static javax.xml.parsers.DocumentBuilder parser() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        return factory.newDocumentBuilder();
    }

    private static String modelNameFor(Class<?> type) { return __.camelToSnake(type.getSimpleName().replaceFirst("Xql$", "")); }
    private static String plural(String value) { return value.endsWith("s") ? value : value + "s"; }
    private static String nonBlank(String value, String message) { if (value == null || value.isBlank()) throw new IllegalArgumentException(message); return value; }
}
