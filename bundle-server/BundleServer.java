import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;

/**
 * Bundle Server - HTTP server for hosting OPA bundles
 * 
 * <p>
 * This lightweight server acts as the central artifact repository in the OPA
 * ecosystem.
 * It provides a simple REST API for:
 * </p>
 * <ul>
 * <li><strong>Uploading (PUT):</strong> Used by the Bundle Builder Service to
 * publish new policy versions.</li>
 * <li><strong>Downloading (GET):</strong> Used by OPA instances to poll and
 * fetch the latest policy bundle.</li>
 * </ul>
 * 
 * <p>
 * The server stores bundles in the local file system at {@code /bundles}.
 * </p>
 */
public class BundleServer {
    private static final int PORT = 8888;
    private static final String BUNDLES_DIR = "/bundles";

    public static void main(String[] args) throws Exception {
        // Ensure bundles directory exists
        Files.createDirectories(Paths.get(BUNDLES_DIR));

        System.out.println("Starting Bundle Server on port " + PORT);
        System.out.println("Bundles directory: " + BUNDLES_DIR);

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // Register handler for /bundles/* endpoints
        server.createContext("/bundles/", new BundleHandler());

        server.setExecutor(null); // Use default executor
        server.start();

        System.out.println("Bundle Server started successfully");
        System.out.println("Endpoints available:");
        System.out.println("  PUT /bundles/{name} - Upload bundle");
        System.out.println("  GET /bundles/{name} - Download bundle");
    }

    static class BundleHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            // Extract bundle name from path: /bundles/{name}
            String bundleName = path.substring("/bundles/".length());

            if (bundleName.isEmpty()) {
                sendResponse(exchange, 400, "Bundle name is required");
                return;
            }

            // Security: Prevent directory traversal
            if (bundleName.contains("..") || bundleName.contains("/")) {
                sendResponse(exchange, 400, "Invalid bundle name");
                return;
            }

            Path bundlePath = Paths.get(BUNDLES_DIR, bundleName);

            try {
                if ("PUT".equals(method)) {
                    handlePut(exchange, bundlePath, bundleName);
                } else if ("GET".equals(method)) {
                    handleGet(exchange, bundlePath, bundleName);
                } else {
                    sendResponse(exchange, 405, "Method not allowed");
                }
            } catch (Exception e) {
                System.err.println("Error handling request: " + e.getMessage());
                e.printStackTrace();
                sendResponse(exchange, 500, "Internal server error: " + e.getMessage());
            }
        }

        /**
         * Handle PUT request - save uploaded bundle
         */
        private void handlePut(HttpExchange exchange, Path bundlePath, String bundleName) throws IOException {
            System.out.println("PUT /bundles/" + bundleName);

            // Read request body
            InputStream requestBody = exchange.getRequestBody();

            // Save to file
            Files.copy(requestBody, bundlePath, StandardCopyOption.REPLACE_EXISTING);

            long fileSize = Files.size(bundlePath);
            System.out.println("Saved bundle: " + bundleName + " (" + fileSize + " bytes)");

            String response = "Bundle uploaded successfully: " + bundleName;
            sendResponse(exchange, 200, response);
        }

        /**
         * Handle GET request - serve bundle file
         */
        private void handleGet(HttpExchange exchange, Path bundlePath, String bundleName) throws IOException {
            System.out.println("GET /bundles/" + bundleName);

            if (!Files.exists(bundlePath)) {
                System.out.println("Bundle not found: " + bundleName);
                sendResponse(exchange, 404, "Bundle not found: " + bundleName);
                return;
            }

            // Set headers for file download
            exchange.getResponseHeaders().set("Content-Type", "application/gzip");
            exchange.getResponseHeaders().set("Content-Disposition",
                    "attachment; filename=\"" + bundleName + "\"");

            byte[] fileBytes = Files.readAllBytes(bundlePath);
            exchange.sendResponseHeaders(200, fileBytes.length);

            OutputStream os = exchange.getResponseBody();
            os.write(fileBytes);
            os.close();

            System.out.println("Served bundle: " + bundleName + " (" + fileBytes.length + " bytes)");
        }

        /**
         * Send text response
         */
        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            byte[] bytes = response.getBytes();
            exchange.sendResponseHeaders(statusCode, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
}
