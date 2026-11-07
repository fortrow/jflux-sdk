/*
 * Copyright (c) 2026 Fortrow LLC.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Fortrow-Commercial
 *
 * JFlux is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License, version 3, as published by
 * the Free Software Foundation.
 *
 * JFlux is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Affero General Public License for
 * more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JFlux. If not, see <https://www.gnu.org/licenses/>.
 *
 * Alternatively, a separate commercial license is available from Fortrow LLC.
 * For terms permitting use without the AGPL copyleft requirements, contact
 * ryan@fortrow.com. Commercial rights require a separate written agreement.
 * See LICENSE and COMMERCIAL-LICENSE.md.
 */

package com.fortrow.jflux.output;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Lightweight JSON-Simple/XML conversion helpers. */
public final class Converter {

    public String jsonToXml(JSONObject json) {
        return jsonToXml(json, "root");
    }

    public String jsonToXml(JSONObject json, String rootName) {
        if (json == null) throw ExceptionHandling.invalid("JSON object is required.");
        validateXmlName(rootName);
        try {
            Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
            Element root = document.createElement(rootName);
            document.appendChild(root);
            appendObject(document, root, json);
            var transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
            transformer.setOutputProperty(OutputKeys.INDENT, "no");
            StringWriter output = new StringWriter();
            transformer.transform(new DOMSource(document), new StreamResult(output));
            return output.toString();
        } catch (Exception e) {
            throw ExceptionHandling.failure("Unable to convert JSON to XML.", e);
        }
    }

    public JSONObject xmlToJson(Document xml) {
        if (xml == null || xml.getDocumentElement() == null) throw ExceptionHandling.invalid("XML document is required.");
        try {
            JSONObject result = new JSONObject();
            result.put(xml.getDocumentElement().getTagName(), elementValue(xml.getDocumentElement()));
            return result;
        } catch (RuntimeException e) {
            throw ExceptionHandling.failure("Unable to convert XML to JSON.", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static void appendObject(Document document, Element parent, Map<?, ?> values) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String name = String.valueOf(entry.getKey());
            validateXmlName(name);
            appendValue(document, parent, name, entry.getValue());
        }
    }

    private static void appendValue(Document document, Element parent, String name, Object value) {
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) appendValue(document, parent, name, item);
            return;
        }
        if (value != null && value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) appendValue(document, parent, name, Array.get(value, i));
            return;
        }
        Element element = document.createElement(name);
        if (value instanceof Map<?, ?> map) appendObject(document, element, map);
        else if (value != null) element.appendChild(document.createTextNode(String.valueOf(value)));
        parent.appendChild(element);
    }

    private static Object elementValue(Element element) {
        List<Element> children = childElements(element);
        if (children.isEmpty()) return element.getTextContent();
        LinkedHashMap<String, Object> values = new LinkedHashMap<>();
        for (Element child : children) {
            String name = child.getTagName();
            Object value = elementValue(child);
            Object existing = values.get(name);
            if (existing == null) values.put(name, value);
            else if (existing instanceof JSONArray array) array.add(value);
            else {
                JSONArray array = new JSONArray();
                array.add(existing);
                array.add(value);
                values.put(name, array);
            }
        }
        return new JSONObject(values);
    }

    private static List<Element> childElements(Element element) {
        ArrayList<Element> children = new ArrayList<>();
        for (Node node = element.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element child) children.add(child);
        }
        return children;
    }

    private static void validateXmlName(String name) {
        if (name == null || !name.matches("[A-Za-z_][A-Za-z0-9_.-]*")) {
            throw ExceptionHandling.invalid("Invalid XML element name.");
        }
    }
}
