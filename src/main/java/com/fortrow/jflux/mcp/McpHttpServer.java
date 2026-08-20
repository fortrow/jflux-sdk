package com.fortrow.jflux.mcp;

import com.fortrow.jflux.exceptions.ExceptionHandling;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

/** JDK-only Streamable HTTP adapter for {@link McpServer}. */
public final class McpHttpServer implements AutoCloseable {

    private final McpServer server;
    private final HttpServer httpServer;

    public McpHttpServer(McpServer server, InetSocketAddress address) {
        if (server == null || address == null) throw ExceptionHandling.invalid("MCP server and address are required.");
        try {
            this.server = server;
            this.httpServer = HttpServer.create(address, 0);
            this.httpServer.createContext("/mcp", this::handle);
        } catch (IOException e) {
            throw ExceptionHandling.failure("Unable to create MCP HTTP server.", e);
        }
    }

    public McpHttpServer executor(Executor executor) {
        if (executor == null) throw ExceptionHandling.invalid("MCP HTTP executor is required.");
        httpServer.setExecutor(executor);
        return this;
    }

    public void start() {
        httpServer.start();
    }

    public void stop(int delaySeconds) {
        httpServer.stop(Math.max(0, delaySeconds));
    }

    public InetSocketAddress address() {
        return httpServer.getAddress();
    }

    @Override
    public void close() {
        stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            exchange.sendResponseHeaders(405, -1);
            exchange.close();
            return;
        }
        String request;
        try (InputStream input = exchange.getRequestBody()) {
            request = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        String response = server.handle(request);
        if (response == null) {
            exchange.sendResponseHeaders(202, -1);
            exchange.close();
            return;
        }
        byte[] body = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Mcp-Protocol-Version", McpServer.PROTOCOL_VERSION);
        exchange.sendResponseHeaders(200, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }
}
