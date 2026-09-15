package com.fortrow.jflux.output.xml;

import com.fortrow.jflux.files.FileObject;

import java.io.File;

/** @deprecated Use {@link com.fortrow.jflux.xml.XML}. */
@Deprecated(since = "1.1.0")
public class XML extends com.fortrow.jflux.xml.XML {

    public XML() { super(); }

    public XML(FileObject file) { super(file); }

    public XML(File file) { super(file); }
}
