
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class Backend {
    public static void main(String[] args) throws IOException {
        int port = 8080;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        // Get version text from ENV, default to "Unknown Version"
        String versionText = System.getenv("VERSION_TEXT");
        if (versionText == null) {
            versionText = "Response from Unknown Version";
        }

        final String responseMessage = versionText;

        server.createContext("/", new HttpHandler() {
            @Override
            public void handle(HttpExchange t) throws IOException {
                String response = responseMessage + "\n";
                t.sendResponseHeaders(200, response.length());
                OutputStream os = t.getResponseBody();
                os.write(response.getBytes());
                os.close();
                System.out.println("Handled request: " + t.getRequestURI());
            }
        });

        server.setExecutor(null); // creates a default executor
        System.out.println("Server started on port " + port);
        System.out.println("Version: " + responseMessage);
        server.start();
    }
}
