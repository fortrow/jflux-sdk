/*
 * -------------------------------------------------------------------------
 * 
 * Copyright (c) 2026 Fortrow LLC. All rights reserved.
 *  
 * NOTICE: All information contained herein is, and remains the property of 
 * Fortrow, LLC. The intellectual and technical concepts contained herein are 
 * proprietary to [Company Name] and may be covered by U.S. and Foreign Patents, 
 * patents in process, and are protected by trade secret or copyright law.
 * 
 * Dissemination of this information or reproduction of this material is 
 * strictly forbidden unless prior written permission is obtained from 
 * Fortrow, LLC.
 * 
 * -------------------------------------------------------------------------
 * 
 * File created by Ryan Fitzgerald (ryan@fortrow.com) on 2026-05-04 15:27:04 EDT
 * 
 * -------------------------------------------------------------------------

 */

package com.fortrow.jflux.output.xml;

import com.fortrow.jflux.files.FileObject;
import org.w3c.dom.Document;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class XML {

    DocumentBuilder builder;
    Document document;

    public XML() {
        try {
            builder = build();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public XML(FileObject file) {
        try {
            builder = build();
            document = builder.parse(file.getFile());
        } catch (Exception e) { e.printStackTrace(); }
    }

    private static DocumentBuilder build() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(true);
        factory.setIgnoringElementContentWhitespace(true);
        return factory.newDocumentBuilder();
    }

    public Document getDocument() {
        return document;
    }
}
