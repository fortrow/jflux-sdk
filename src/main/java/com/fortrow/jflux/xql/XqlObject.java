package com.fortrow.jflux.xql;

import java.util.ArrayList;
import java.util.List;

/** Named, nested XML object used to compose an {@link XqlModel}. */
public class XqlObject {
    private String name;
    private final List<Object> children = new ArrayList<>();
    private boolean required;
    private boolean multiple;

    protected XqlObject(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Object name is required.");
        this.name = name;
    }

    public String name() { return name; }

    /** Allows a root model to derive its name after Java has established its runtime type. */
    protected final void name(String name) {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Object name is required.");
        this.name = name;
    }
    public boolean isRequired() { return required; }
    public boolean isMultiple() { return multiple; }
    public List<Object> children() { return List.copyOf(children); }

    public XqlField field(String name) {
        XqlField field = new XqlField(name);
        children.add(field);
        return field;
    }

    public XqlObject group(String name) {
        XqlObject group = new XqlObject(name);
        children.add(group);
        return group;
    }

    public XqlObject required() { required = true; return this; }
    public XqlObject multiple() { multiple = true; return this; }

    protected void addChild(Object child) { children.add(child); }
}
