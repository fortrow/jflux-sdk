package com.fortrow.jflux.exceptions.reporting.types;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.fortrow.jflux.exceptions.types.ReportedException;
import com.fortrow.jflux.mcp.McpServer;

/** Exception reporter that stores structured reports for MCP model clients. */
public final class McpReporter {

    private static volatile McpServer server;

    private McpReporter() { }

    /** Enables MCP reporting and attaches the application-owned MCP server. */
    public static void setConfiguration(McpServer value) {
        if (value == null) throw ExceptionHandling.invalid("MCP server is required.");
        server = value;
    }

    public static McpServer getServer() {
        return server;
    }

    public static boolean hasConfiguration() {
        return server != null;
    }

    public static boolean report(ReportedException exception) {
        McpServer value = server;
        return value != null && value.record(exception);
    }

    public static void disable() {
        server = null;
    }
}
