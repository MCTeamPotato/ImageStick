package me.kall.imageStick;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.util.concurrent.Executors;

public class ImageHttpServer {
    private final File imagesRoot;
    private final int port;
    private HttpServer server;

    public ImageHttpServer(File imagesRoot, int port) {
        this.imagesRoot = imagesRoot;
        this.port = port;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new FileHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }

    private class FileHandler implements HttpHandler {
        @Override
        public void handle(@NotNull HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                respond(exchange, 405, "Method Not Allowed");
                return;
            }

            URI uri = exchange.getRequestURI();

            String rawPath = uri.getPath();

            if (rawPath.startsWith("/")) {
                rawPath = rawPath.substring(1);
            }

            if (rawPath.contains("..")) {
                respond(exchange, 403, "Forbidden");
                return;
            }

            File requestedFile = new File(imagesRoot, rawPath);

            if (!requestedFile.exists() || !requestedFile.isFile()) {
                respond(exchange, 404, "Not Found: " + rawPath);
                return;
            }

            if (!requestedFile.getCanonicalPath().startsWith(imagesRoot.getCanonicalPath())) {
                respond(exchange, 403, "Forbidden");
                return;
            }

            String contentType = Files.probeContentType(requestedFile.toPath());
            if (contentType == null) contentType = "application/octet-stream";

            byte[] data = Files.readAllBytes(requestedFile.toPath());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }

        private void respond(@NotNull HttpExchange exchange, int code, @NotNull String message) throws IOException {
            byte[] bytes = message.getBytes();
            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
