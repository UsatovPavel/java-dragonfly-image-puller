package ru.hse.dragonfly.puller;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

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
    private static final String BIN_SUFFIX = ".bin";

    @Test
    void pullRegistryRequestMapsAndDelegatesToBlobPuller() throws Exception {
        Path output = Files.createTempFile("puller-facade-", BIN_SUFFIX);
        Files.deleteIfExists(output);
        Path returnedPath = Files.createTempFile("puller-facade-result-", BIN_SUFFIX);
        Files.deleteIfExists(returnedPath);

        AtomicReference<PullRequest> captured = new AtomicReference<>();
        try (DragonflyImagePuller puller = new DragonflyImagePuller(new FakeBlobPullGateway(
                captured,
                ignored -> CompletableFuture.completedFuture(new PullResult(returnedPath))
        ))) {
            PullResult result = puller.pull(new RegistryPullRequest(
                    "registry.example.com",
                    "repo/image",
                    null,
                    "sha256:abc123",
                    RegistryAuth.none(),
                    output
            )).join();
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
        Path output = Files.createTempFile("puller-facade-invalid-", BIN_SUFFIX);
        Files.deleteIfExists(output);

        try (DragonflyImagePuller puller = new DragonflyImagePuller(new FakeBlobPullGateway(
                new AtomicReference<>(),
                ignored -> CompletableFuture.completedFuture(new PullResult(output))
        ))) {
            CompletionException completionException = assertThrows(
                    CompletionException.class,
                    () -> puller.pull(new RegistryPullRequest(
                            "registry.example.com",
                            "repo",
                            null,
                            " ",
                            RegistryAuth.none(),
                            output
                    )).join()
            );
            DragonflyPullException ex = (DragonflyPullException) completionException.getCause();
            assertEquals(DragonflyPullErrorKind.INVALID_REQUEST, ex.errorKind());
        } finally {
            Files.deleteIfExists(output);
        }
    }

    @Test
    void pullRegistryRequestBearerAuthSetsAuthorizationHeader() throws Exception {
        Path output = Files.createTempFile("puller-facade-auth-", BIN_SUFFIX);
        Files.deleteIfExists(output);
        Path returnedPath = Files.createTempFile("puller-facade-auth-result-", BIN_SUFFIX);
        Files.deleteIfExists(returnedPath);

        AtomicReference<PullRequest> captured = new AtomicReference<>();
        RegistryAuth auth = RegistryAuth.bearer("jwt-token");
        try (DragonflyImagePuller puller = new DragonflyImagePuller(new FakeBlobPullGateway(
                captured,
                ignored -> CompletableFuture.completedFuture(new PullResult(returnedPath))
        ))) {
            puller.pull(new RegistryPullRequest(
                    "https://registry.example.com",
                    "repo/image",
                    null,
                    "sha256:def456",
                    auth,
                    output
            )).join();
            String authorization = captured.get().headers().get("Authorization");
            assertEquals("Bearer jwt-token", authorization);
            assertTrue(!authorization.startsWith("Basic "), "bearer auth should not produce basic header");
        } finally {
            Files.deleteIfExists(output);
            Files.deleteIfExists(returnedPath);
        }
    }

    @Test
    void pullAllRegistryReturnsOrderedResults() throws Exception {
        Path output1 = Files.createTempFile("puller-facade-batch-1-", BIN_SUFFIX);
        Path output2 = Files.createTempFile("puller-facade-batch-2-", BIN_SUFFIX);
        Files.deleteIfExists(output1);
        Files.deleteIfExists(output2);

        try (DragonflyImagePuller puller = new DragonflyImagePuller(new FakeBlobPullGateway(
                new AtomicReference<>(),
                request -> CompletableFuture.completedFuture(new PullResult(request.outputPath()))
        ))) {
            List<PullResult> results = puller.pullAllRegistry(List.of(
                    new RegistryPullRequest("registry.example.com", "repo/one", null, "sha256:aaa", RegistryAuth.none(), output1),
                    new RegistryPullRequest("registry.example.com", "repo/two", null, "sha256:bbb", RegistryAuth.none(), output2)
            )).join();

            assertEquals(2, results.size());
            assertEquals(output1, results.get(0).path());
            assertEquals(output2, results.get(1).path());
        } finally {
            Files.deleteIfExists(output1);
            Files.deleteIfExists(output2);
        }
    }

    @Test
    void pullAllFailsWhenAnyRequestFails() throws Exception {
        Path output1 = Files.createTempFile("puller-facade-batch-fail-1-", BIN_SUFFIX);
        Path output2 = Files.createTempFile("puller-facade-batch-fail-2-", BIN_SUFFIX);
        Files.deleteIfExists(output1);
        Files.deleteIfExists(output2);

        try (DragonflyImagePuller puller = new DragonflyImagePuller(new FakeBlobPullGateway(
                new AtomicReference<>(),
                request -> request.digest().contains("bad")
                        ? CompletableFuture.failedFuture(new DragonflyPullException(
                                DragonflyPullErrorKind.INTERNAL,
                                "forced failure"
                        ))
                        : CompletableFuture.completedFuture(new PullResult(request.outputPath()))
        ))) {
            CompletionException exception = assertThrows(
                    CompletionException.class,
                    () -> puller.pullAll(List.of(
                            new PullRequest("https://registry.example.com/v2/repo/blobs/sha256:good", "sha256:good", output1, java.util.Map.of()),
                            new PullRequest("https://registry.example.com/v2/repo/blobs/sha256:bad", "sha256:bad", output2, java.util.Map.of())
                    )).join()
            );
            assertTrue(exception.getCause() instanceof DragonflyPullException);
            DragonflyPullException cause = (DragonflyPullException) exception.getCause();
            assertEquals(DragonflyPullErrorKind.INTERNAL, cause.errorKind());
        } finally {
            Files.deleteIfExists(output1);
            Files.deleteIfExists(output2);
        }
    }

    private static final class FakeBlobPullGateway implements BlobPullGateway {
        private final AtomicReference<PullRequest> captured;
        private final Function<PullRequest, CompletableFuture<PullResult>> responder;

        private FakeBlobPullGateway(
                AtomicReference<PullRequest> captured,
                Function<PullRequest, CompletableFuture<PullResult>> responder
        ) {
            this.captured = captured;
            this.responder = responder;
        }

        @Override
        public CompletableFuture<PullResult> pull(PullRequest request) {
            captured.set(request);
            return responder.apply(request);
        }

        @Override
        public void close() throws IOException {
            // no-op
        }
    }
}
