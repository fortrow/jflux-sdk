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

package com.fortrow.jflux.xql;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.util.ArrayList;
import java.util.List;

/** A deterministic XML schema migration operation. */
public interface XqlMigration {
    void apply(Document document) throws XqlException;

    static XqlMigration rename(String from, String to) {
        return document -> {
            Element source = find(document, from);
            if (source == null) return;
            String name = last(to);
            Element replacement = document.createElement(name);
            while (source.getFirstChild() != null) replacement.appendChild(source.getFirstChild());
            for (int i = 0; i < source.getAttributes().getLength(); i++) {
                Node attribute = source.getAttributes().item(i);
                replacement.setAttribute(attribute.getNodeName(), attribute.getNodeValue());
            }
            if (source.getParentNode() != null) source.getParentNode().replaceChild(replacement, source);
        };
    }

    static XqlMigration add(String path, Object defaultValue) {
        return document -> {
            String[] parts = parts(path);
            if (parts.length == 0) return;
            Element parent = document.getDocumentElement();
            int start = parts[0].equals(parent.getTagName()) ? 1 : 0;
            for (int i = start; i < parts.length - 1; i++) {
                Element next = child(parent, parts[i]);
                if (next == null) { next = document.createElement(parts[i]); parent.appendChild(next); }
                parent = next;
            }
            String field = parts[parts.length - 1];
            if (child(parent, field) == null) {
                Element created = document.createElement(field);
                if (defaultValue != null) created.setTextContent(String.valueOf(defaultValue));
                parent.appendChild(created);
            }
        };
    }

    static XqlMigration remove(String path) {
        return document -> { Element node = find(document, path); if (node != null && node.getParentNode() != null) node.getParentNode().removeChild(node); };
    }

    static XqlMigration transform(String path, java.util.function.UnaryOperator<String> transform) {
        return document -> { Element node = find(document, path); if (node != null) node.setTextContent(transform.apply(node.getTextContent())); };
    }

    private static Element find(Document document, String path) {
        String[] parts = parts(path); if (parts.length == 0) return null;
        Element current = document.getDocumentElement();
        int start = parts[0].equals(current.getTagName()) ? 1 : 0;
        for (int i = start; i < parts.length; i++) { current = child(current, parts[i]); if (current == null) return null; }
        return current;
    }
    private static Element child(Element parent, String name) {
        NodeList nodes = parent.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) { Node node = nodes.item(i); if (node instanceof Element e && e.getTagName().equals(name)) return e; }
        return null;
    }
    private static String[] parts(String path) {
        if (path == null || path.isBlank()) return new String[0];
        String normalized = path.replace('\\', '/').strip().replaceAll("^/+|/+$", "");
        return normalized.isEmpty() ? new String[0] : normalized.split("/");
    }
    private static String last(String path) { String[] values = parts(path); return values.length == 0 ? "" : values[values.length - 1]; }
}
