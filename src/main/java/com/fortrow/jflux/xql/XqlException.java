package com.fortrow.jflux.xql;

import com.fortrow.jflux.exceptions.types.CommonException;

/** Raised when an XQL model definition or persistence operation is invalid. */
public class XqlException extends CommonException {
    public XqlException(String message) { super(message); }
    public XqlException(String message, Throwable cause) { super(message, cause); }
}
