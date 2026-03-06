package ru.hse.dragonfly.puller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import ru.hse.dragonfly.puller.blobpuller.BlobPullGateway;
import ru.hse.dragonfly.puller.blobpuller.PullRequest;
import ru.hse.dragonfly.puller.blobpuller.PullResult;
import ru.hse.dragonfly.puller.error.DragonflyPullErrorKind;
import ru.hse.dragonfly.puller.error.DragonflyPullException;
import ru.hse.dragonfly.puller.registry.RegistryAuth;
import ru.hse.dragonfly.puller.registry.RegistryPullRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DragonflyImagePullerFacadeTest {

    @Test
    void pullRegistryRequestMapsAndDelegatesToBlobPuller() throws Exception {
        Path output = Files.createTempFile("puller-facade-", ".bin");
        Files.deleteIfExists(output);
        Path returnedPath = Files.createTempFile("puller-facade-result-", ".bin");
        Files.deleteIfExists(returnedPath);

        AtomicReference<PullRequest> captured = new AtomicReference<>();
        try (DragonflyImagePuller puller = new DragonflyImagePuller(new FakeBlobPullGateway(captured, returnedPath))) {
            PullResult result = puller.pull(new RegistryPullRequest(
                    "registry.example.com",
                    "repo/image",
                    null,
                    "sha256:abc123",
                    RegistryAuth.none(),
                    output
            ));
            PullRequest delegated = captured.get();
            assertEquals(returnedPath, result.path());
            assertEquals("https://registry.example.com/v2/repo/image/blobs/sha256:abc123", delegated.blobUrl());
            assertEquals("sha256:abc123", delegated.digest());
            assertEquals(output, delegated.outputPath());
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(returnedPath);
        }
    }

    @Test
    void pullRegistryRequestWithInvalidInputReturnsInvalidRequest() throws Exception {
        Path output = Files.createTempFile("puller-facade-invalid-", ".bin");
        Files.deleteIfExists(output);

        try (DragonflyImagePuller puller = new DragonflyImagePuller(new FakeBlobPullGateway(new AtomicReference<>(), output))) {
            DragonflyPullException ex = assertThrows(
                    DragonflyPullException.class,
                    () -> puller.pull(new RegistryPullRequest(
                            "registry.example.com",
                            "repo",
                            null,
                            " ",
                            RegistryAuth.none(),
                            output
                    ))
            );
            assertEquals(DragonflyPullErrorKind.INVALID_REQUEST, ex.errorKind());
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    void pullRegistryRequestJwtHasPriorityOverBasic() throws Exception {
        Path output = Files.createTempFile("puller-facade-auth-", ".bin");
        Files.deleteIfExists(output);
        Path returnedPath = Files.createTempFile("puller-facade-auth-result-", ".bin");
        Files.deleteIfExists(returnedPath);

        AtomicReference<PullRequest> captured = new AtomicReference<>();
        RegistryAuth auth = new RegistryAuth("user", "pass", "jwt-token");
        try (DragonflyImagePuller puller = new DragonflyImagePuller(new FakeBlobPullGateway(captured, returnedPath))) {
            puller.pull(new RegistryPullRequest(
                    "https://registry.example.com",
                    "repo/image",
                    null,
                    "sha256:def456",
                    auth,
                    output
            ));
            String authorization = captured.get().headers().get("Authorization");
            assertEquals("Bearer jwt-token", authorization);
            assertTrue(!authorization.startsWith("Basic "), "jwt should have priority over basic auth");
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(returnedPath);
        }
    }

    private static final class FakeBlobPullGateway implements BlobPullGateway {
        private final AtomicReference<PullRequest> captured;
        private final Path resultPath;

        private FakeBlobPullGateway(AtomicReference<PullRequest> captured, Path resultPath) {
            this.captured = captured;
            this.resultPath = resultPath;
        }

        @Override
        public PullResult pull(PullRequest request) {
            captured.set(request);
            return new PullResult(resultPath);
        }

        @Override
        public void close() throws IOException {
            // no-op
        }
    }
}
