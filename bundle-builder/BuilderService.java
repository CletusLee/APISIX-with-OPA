import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.util.zip.GZIPOutputStream;
import org.apache.commons.compress.archivers.tar.*;

/**
 * Bundle Builder Service - Assembles and uploads OPA bundles
 * 
 * This service implements a "File-Based Hybrid" workflow:
 * 1. Reads static policy files from mounted repository
 * 2. Reads dynamic canary configuration from data.json
 * 3. Packages policies and data into a tar.gz bundle
 * 4. Uploads bundle to Bundle Server
 * 
 * The builder runs in a loop, rebuilding and uploading bundles every 10
 * seconds.
 * 
 * IMPORTANT PRODUCTION NOTE:
 * In this PoC, canary data is read from a static file for simplicity.
 * In production, this data should be fetched from a Backend Service API
 * to support real-time runtime updates without file modifications.
 */
public class BuilderService {
    // Configuration
    private static final String REPO_MOUNT_PATH = "/app/repo";
    private static final String PLATFORM_POLICY = REPO_MOUNT_PATH + "/policies/platform/platform.rego";
    private static final String SERVICE_POLICY = REPO_MOUNT_PATH + "/policies/backend/policy.rego";
    private static final String CANARY_DATA_FILE = REPO_MOUNT_PATH + "/policies/backend/data.json";

    private static final String BUNDLE_SERVER_URL = "http://bundle-server:8888/bundles/authz.tar.gz";
    private static final int BUILD_INTERVAL_SECONDS = 10;

    public static void main(String[] args) throws Exception {
        System.out.println("=".repeat(60));
        System.out.println("Bundle Builder Service");
        System.out.println("=".repeat(60));
        System.out.println("Repo mount path: " + REPO_MOUNT_PATH);
        System.out.println("Build interval: " + BUILD_INTERVAL_SECONDS + " seconds");
        System.out.println("Bundle server: " + BUNDLE_SERVER_URL);
        System.out.println("=".repeat(60));

        // Main loop
        while (true) {
            try {
                System.out.println("\n[" + java.time.LocalDateTime.now() + "] Starting bundle build...");
                buildAndUploadBundle();
                System.out.println("[" + java.time.LocalDateTime.now() + "] Bundle build complete");
            } catch (Exception e) {
                System.err.println("Error during bundle build:");
                e.printStackTrace();
            }

            Thread.sleep(BUILD_INTERVAL_SECONDS * 1000);
        }
    }

    /**
     * Main build process:
     * 1. Read policies from mounted repo
     * 2. Read canary data from file
     * 3. Package into tar.gz
     * 4. Upload to Bundle Server
     */
    private static void buildAndUploadBundle() throws Exception {
        // Step 1: Read policy files
        System.out.println("Step 1: Reading policy files...");
        String platformPolicy = readFile(PLATFORM_POLICY);
        String servicePolicy = readFile(SERVICE_POLICY);
        System.out.println("  ✓ Platform policy: " + platformPolicy.length() + " bytes");
        System.out.println("  ✓ Service policy: " + servicePolicy.length() + " bytes");

        // Step 2: Read canary data
        System.out.println("Step 2: Reading canary configuration...");

        // TODO: In Production, fetch this JSON from the Backend Service API
        // (e.g., GET /internal/canary-config) to support real-time runtime updates.
        // This would allow dynamic changes to allowlist_users and rollout_percentage
        // without file modifications or redeployments.
        //
        // Example production code:
        // URL apiUrl = new URL("http://backend:8080/internal/canary-config");
        // HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
        // String canaryData = readInputStream(conn.getInputStream());
        //
        String canaryData = readFile(CANARY_DATA_FILE);
        System.out.println("  ✓ Canary data: " + canaryData.length() + " bytes");
        System.out.println("  ℹ Note: Reading from file. In production, fetch from API.");

        // Step 3: Package bundle
        System.out.println("Step 3: Packaging bundle...");
        byte[] bundleBytes = createBundle(platformPolicy, servicePolicy, canaryData);
        System.out.println("  ✓ Bundle size: " + bundleBytes.length + " bytes");

        // Step 4: Upload to Bundle Server
        System.out.println("Step 4: Uploading to Bundle Server...");
        uploadBundle(bundleBytes);
        System.out.println("  ✓ Upload successful");
    }

    /**
     * Create a tar.gz bundle with the following structure:
     * /policy/platform.rego
     * /policy/service.rego
     * /data/data.json
     */
    private static byte[] createBundle(String platformPolicy, String servicePolicy, String canaryData)
            throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        GZIPOutputStream gzipOut = new GZIPOutputStream(baos);
        TarArchiveOutputStream tarOut = new TarArchiveOutputStream(gzipOut);

        // Set long file mode to support file names longer than 100 characters
        tarOut.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX);

        try {
            // Add platform.rego to /policy/
            addToTar(tarOut, "policy/platform.rego", platformPolicy.getBytes());

            // Add service.rego to /policy/
            addToTar(tarOut, "policy/service.rego", servicePolicy.getBytes());

            // Add data.json to /data/
            addToTar(tarOut, "data/data.json", canaryData.getBytes());

            tarOut.finish();
            tarOut.close();
            gzipOut.close();

            return baos.toByteArray();
        } finally {
            tarOut.close();
        }
    }

    /**
     * Add a file entry to the tar archive
     */
    private static void addToTar(TarArchiveOutputStream tarOut, String entryName, byte[] content) throws Exception {
        TarArchiveEntry entry = new TarArchiveEntry(entryName);
        entry.setSize(content.length);
        tarOut.putArchiveEntry(entry);
        tarOut.write(content);
        tarOut.closeArchiveEntry();
    }

    /**
     * Upload bundle to Bundle Server via PUT request
     */
    private static void uploadBundle(byte[] bundleBytes) throws Exception {
        URL url = new URL(BUNDLE_SERVER_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("PUT");
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/gzip");
            conn.setRequestProperty("Content-Length", String.valueOf(bundleBytes.length));

            OutputStream os = conn.getOutputStream();
            os.write(bundleBytes);
            os.close();

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("Upload failed with status code: " + responseCode);
            }

            // Read response
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String response = br.readLine();
            br.close();

            System.out.println("  Server response: " + response);
        } finally {
            conn.disconnect();
        }
    }

    /**
     * Read file contents as string
     */
    private static String readFile(String path) throws Exception {
        Path filePath = Paths.get(path);
        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("File not found: " + path);
        }
        return new String(Files.readAllBytes(filePath));
    }
}
