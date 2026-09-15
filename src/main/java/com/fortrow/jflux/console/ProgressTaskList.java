package com.fortrow.jflux.console;

import java.io.PrintStream;

/**
 * @deprecated Progress output belongs to {@link com.fortrow.jflux.stdout.ProgressTaskList}.
 * Use that class for new code; this facade keeps existing CLI imports source-compatible.
 */
@Deprecated(since = "1.1.0", forRemoval = false)
public final class ProgressTaskList extends com.fortrow.jflux.stdout.ProgressTaskList {

    public ProgressTaskList() {
        super();
    }

    public ProgressTaskList(PrintStream out) {
        super(out);
    }
}
