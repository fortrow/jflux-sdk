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

package com.fortrow.jflux.xml;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.files.FileObject;
import org.w3c.dom.Document;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;

/** Secure DOM XML loader with external entity and external DTD access disabled. */
public class XML {

    private final DocumentBuilder builder;
    private Document document;

    public XML() {
        this.builder = build();
    }

    public XML(FileObject file) {
        this();
        if (file == null || file.getFile() == null) throw ExceptionHandling.invalid("XML file is required.");
        parse(file.getFile());
    }

    public XML(File file) {
        this();
        parse(file);
    }

    public Document getDocument() { return document; }

    public Document parse(File file) {
        if (file == null || !file.isFile()) throw ExceptionHandling.invalid("XML file must exist.");
        try {
            document = builder.parse(file);
            return document;
        } catch (Exception e) {
            throw ExceptionHandling.failure("Unable to parse XML document.", e);
        }
    }

    private static DocumentBuilder build() {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            return factory.newDocumentBuilder();
        } catch (Exception e) {
            throw ExceptionHandling.failure("Unable to initialize secure XML parser.", e);
        }
    }
}
