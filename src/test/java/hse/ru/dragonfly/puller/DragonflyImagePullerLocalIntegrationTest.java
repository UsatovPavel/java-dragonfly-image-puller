package hse.ru.dragonfly.puller;

import com.sun.net.httpserver.HttpServer;
import hse.ru.dragonfly.puller.error.DragonflyPullException;
import hse.ru.dragonfly.puller.model.PullRequest;
import hse.ru.dragonfly.puller.model.PullResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("local")
class DragonflyImagePullerLocalIntegrationTest {
    private static final String DFDAEMON_ADDR = System.getenv()
            .getOrDefault("DFDAEMON_ADDR", "unix:///var/run/dragonfly/dfdaemon.sock");

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void secondPullUsesDfdaemonCache() throws Exception {
        ensureDfdaemonAvailable(DFDAEMON_ADDR);

        byte[] payload = "java-dragonfly-image-puller-cache-test".getBytes(StandardCharsets.UTF_8);
        String digest = "sha256:" + sha256(payload);
        AtomicInteger blobRequests = new AtomicInteger();
        startServer(payload, digest, blobRequests);

        Path outputDir = resolveOutputDir(DFDAEMON_ADDR);
        Assumptions.assumeTrue(Files.exists(outputDir),
                () -> "output dir does not exist: " + outputDir
                        + " (set DFDAEMON_OUTPUT_DIR or prepare single-cluster output mount)");

        String blobUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v2/repo/blobs/" + digest;
        Path firstOutput = Files.createTempFile(outputDir, "pull-1-", ".bin");
        Path secondOutput = Files.createTempFile(outputDir, "pull-2-", ".bin");
        Files.deleteIfExists(firstOutput);
        Files.deleteIfExists(secondOutput);

        try (DragonflyImagePuller puller = DragonflyImagePuller.builder()
                .withAddress(DFDAEMON_ADDR)
                .withRequestTimeout(Duration.ofSeconds(30))
                .withMaxRetries(1)
                .build()) {
            PullResult first = puller.pull(new PullRequest(blobUrl, digest, firstOutput, Map.of()));
            assertTrue(Files.exists(first.path()), "first pull should create output file");
            assertEquals(payload.length, Files.size(first.path()), "first pull size must match");
            assertTrue(blobRequests.get() >= 1, "first pull should hit source at least once");

            int requestsAfterFirstPull = blobRequests.get();

            PullResult second = puller.pull(new PullRequest(blobUrl, digest, secondOutput, Map.of()));
            assertTrue(Files.exists(second.path()), "second pull should create output file");
            assertEquals(payload.length, Files.size(second.path()), "second pull size must match");
            assertEquals(requestsAfterFirstPull, blobRequests.get(),
                    "second pull should be served from dfdaemon cache");
        }
    }

    private void startServer(byte[] payload, String digest, AtomicInteger blobRequests) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v2/", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(200, 0);
            }
        });
        server.createContext("/v2/repo/blobs/" + digest, exchange -> {
            try (exchange) {
                blobRequests.incrementAndGet();
                exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                exchange.getResponseHeaders().add("Content-Length", String.valueOf(payload.length));
                exchange.sendResponseHeaders(200, payload.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(payload);
                }
            }
        });
        server.start();
    }

    private static void ensureDfdaemonAvailable(String address) {
        if (address.startsWith("unix://")) {
            String socketPath = address.substring("unix://".length()).trim();
            Assumptions.assumeTrue(Files.exists(Path.of(socketPath)),
                    () -> "dfdaemon socket not found at " + socketPath);
            return;
        }
        int colon = address.lastIndexOf(':');
        if (colon <= 0) {
            Assumptions.assumeTrue(false, () -> "invalid DFDAEMON_ADDR: " + address);
            return;
        }
        String host = address.substring(0, colon).trim();
        int port = Integer.parseInt(address.substring(colon + 1).trim());
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
        } catch (IOException ex) {
            Assumptions.assumeTrue(false, () -> "dfdaemon gRPC not reachable at " + address);
        }
    }

    private static Path resolveOutputDir(String address) {
        String envOutputDir = System.getenv("DFDAEMON_OUTPUT_DIR");
        if (envOutputDir != null && !envOutputDir.isBlank()) {
            return Path.of(envOutputDir);
        }
        if (address.startsWith("unix://")) {
            String socketPath = address.substring("unix://".length()).trim();
            Path socket = Path.of(socketPath);
            Path parent = socket.getParent();
            if (parent != null) {
                return parent.resolve("output");
            }
        }
        return Path.of("/tmp");
    }

    private static String sha256(byte[] bytes) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(bytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : digest) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
