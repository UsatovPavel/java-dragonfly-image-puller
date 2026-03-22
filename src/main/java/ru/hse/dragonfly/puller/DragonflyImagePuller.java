package ru.hse.dragonfly.puller;

import ru.hse.dragonfly.puller.error.DragonflyPullException;
import ru.hse.dragonfly.puller.error.DragonflyPullErrorKind;
import ru.hse.dragonfly.puller.blobpuller.BlobPullGateway;
import ru.hse.dragonfly.puller.grpc.config.GrpcClientConfig;
import ru.hse.dragonfly.puller.grpc.dfdaemon.DfdaemonDownloadClient;
import ru.hse.dragonfly.puller.blobpuller.PullRequest;
import ru.hse.dragonfly.puller.blobpuller.PullResult;
import ru.hse.dragonfly.puller.registry.RegistryPullRequest;
import ru.hse.dragonfly.puller.registry.RegistryPullRequestMapper;
import org.jetbrains.annotations.NotNull;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public final class DragonflyImagePuller implements Closeable {
    private final BlobPullGateway blobPuller;

    DragonflyImagePuller(BlobPullGateway blobPuller) {
        this.blobPuller = blobPuller;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static DragonflyImagePuller createDefault() throws DragonflyPullException {
        return builder().build();
    }

    public @NotNull CompletableFuture<PullResult> pull(@NotNull PullRequest request) {
        return blobPuller.pull(request);
    }

    public @NotNull CompletableFuture<PullResult> pull(@NotNull RegistryPullRequest request) {
        try {
            PullRequest transportRequest = RegistryPullRequestMapper.toTransportRequest(request);
            return blobPuller.pull(transportRequest);
        } catch (IllegalArgumentException ex) {
            return CompletableFuture.failedFuture(
                    new DragonflyPullException(DragonflyPullErrorKind.INVALID_REQUEST, ex.getMessage(), ex)
            );
        }
    }

    public @NotNull CompletableFuture<List<PullResult>> pullAll(@NotNull List<@NotNull PullRequest> requests) {
        List<CompletableFuture<PullResult>> futures = new ArrayList<>(requests.size());
        for (PullRequest request : requests) {
            futures.add(blobPuller.pull(request));
        }
        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        return allDone.thenApply(ignored -> {
            List<PullResult> results = new ArrayList<>(futures.size());
            for (CompletableFuture<PullResult> future : futures) {
                results.add(future.join());
            }
            return results;
        });
    }

    public @NotNull CompletableFuture<List<PullResult>> pullAllRegistry(
            @NotNull List<@NotNull RegistryPullRequest> requests
    ) {
        List<CompletableFuture<PullResult>> futures = new ArrayList<>(requests.size());
        for (RegistryPullRequest request : requests) {
            futures.add(pull(request));
        }
        CompletableFuture<Void> allDone = CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new));
        return allDone.thenApply(ignored -> {
            List<PullResult> results = new ArrayList<>(futures.size());
            for (CompletableFuture<PullResult> future : futures) {
                results.add(future.join());
            }
            return results;
        });
    }

    @Override
    public void close() throws IOException {
        blobPuller.close();
    }

    public static final class Builder {
        private static final String DEFAULT_ADDRESS = System.getenv()
                .getOrDefault("DFDAEMON_ADDR", "unix:///var/run/dragonfly/dfdaemon.sock");
        private static final int DEFAULT_MAX_RETRIES = 1;

        private String configuredAddress = DEFAULT_ADDRESS;
        private Duration configuredRequestTimeout;
        private int configuredMaxRetries = DEFAULT_MAX_RETRIES;
        private GrpcClientConfig configuredGrpcConfig = GrpcClientConfig.defaults();

        public Builder withAddress(String value) {
            this.configuredAddress = value;
            return this;
        }

        public Builder withRequestTimeout(Duration value) {
            this.configuredRequestTimeout = value;
            return this;
        }

        public Builder withMaxRetries(int value) {
            this.configuredMaxRetries = value;
            return this;
        }

        public Builder withGrpcKeepAliveTime(Duration value) {
            this.configuredGrpcConfig = configuredGrpcConfig.withKeepAliveTime(value);
            return this;
        }

        public Builder withGrpcKeepAliveTimeout(Duration value) {
            this.configuredGrpcConfig = configuredGrpcConfig.withKeepAliveTimeout(value);
            return this;
        }

        public Builder withGrpcInitialRetryBackoff(Duration value) {
            this.configuredGrpcConfig = configuredGrpcConfig.withInitialRetryBackoff(value);
            return this;
        }

        public Builder withGrpcMaxRetryBackoff(Duration value) {
            this.configuredGrpcConfig = configuredGrpcConfig.withMaxRetryBackoff(value);
            return this;
        }

        public Builder withGrpcRetryBackoffMultiplier(double value) {
            this.configuredGrpcConfig = configuredGrpcConfig.withRetryBackoffMultiplier(value);
            return this;
        }

        public DragonflyImagePuller build() throws DragonflyPullException {
            validate();
            DfdaemonDownloadClient client = new DfdaemonDownloadClient(
                    configuredAddress,
                    configuredRequestTimeout,
                    configuredMaxRetries,
                    configuredGrpcConfig
            );
            return new DragonflyImagePuller(client);
        }

        private void validate() throws DragonflyPullException {
            if (configuredAddress == null || configuredAddress.isBlank()) {
                throw new DragonflyPullException(DragonflyPullErrorKind.INVALID_REQUEST, "address must not be blank");
            }
            if (configuredRequestTimeout != null
                    && (configuredRequestTimeout.isZero() || configuredRequestTimeout.isNegative())) {
                throw new DragonflyPullException(DragonflyPullErrorKind.INVALID_REQUEST, "requestTimeout must be positive");
            }
            if (configuredMaxRetries < 0) {
                throw new DragonflyPullException(DragonflyPullErrorKind.INVALID_REQUEST, "maxRetries must be >= 0");
            }
            configuredGrpcConfig.validate();
        }
    }
}
