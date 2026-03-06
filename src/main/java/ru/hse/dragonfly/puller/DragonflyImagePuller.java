package ru.hse.dragonfly.puller;

import ru.hse.dragonfly.puller.error.DragonflyPullException;
import ru.hse.dragonfly.puller.error.DragonflyPullErrorKind;
import ru.hse.dragonfly.puller.blobpuller.BlobPuller;
import ru.hse.dragonfly.puller.blobpuller.BlobPullGateway;
import ru.hse.dragonfly.puller.grpcdfdaemon.DfdaemonDownloadClient;
import ru.hse.dragonfly.puller.blobpuller.PullRequest;
import ru.hse.dragonfly.puller.blobpuller.PullResult;
import ru.hse.dragonfly.puller.registry.RegistryPullRequest;
import ru.hse.dragonfly.puller.registry.RegistryPullRequestMapper;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;

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

    public PullResult pull(PullRequest request) throws DragonflyPullException {
        return blobPuller.pull(request);
    }

    public PullResult pull(RegistryPullRequest request) throws DragonflyPullException {
        try {
            PullRequest transportRequest = RegistryPullRequestMapper.toTransportRequest(request);
            return blobPuller.pull(transportRequest);
        } catch (IllegalArgumentException ex) {
            throw new DragonflyPullException(DragonflyPullErrorKind.INVALID_REQUEST, ex.getMessage(), ex);
        }
    }

    @Override
    public void close() throws IOException {
        blobPuller.close();
    }

    public static final class Builder {
        private static final String DEFAULT_ADDRESS = System.getenv()
                .getOrDefault("DFDAEMON_ADDR", "unix:///var/run/dragonfly/dfdaemon.sock");
        private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(120);
        private static final int DEFAULT_MAX_RETRIES = 1;

        private String configuredAddress = DEFAULT_ADDRESS;
        private Duration configuredRequestTimeout = DEFAULT_REQUEST_TIMEOUT;
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
            return new DragonflyImagePuller(new BlobPuller(client));
        }

        private void validate() throws DragonflyPullException {
            if (configuredAddress == null || configuredAddress.isBlank()) {
                throw new DragonflyPullException(DragonflyPullErrorKind.INVALID_REQUEST, "address must not be blank");
            }
            if (configuredRequestTimeout == null
                    || configuredRequestTimeout.isZero()
                    || configuredRequestTimeout.isNegative()) {
                throw new DragonflyPullException(DragonflyPullErrorKind.INVALID_REQUEST, "requestTimeout must be positive");
            }
            if (configuredMaxRetries < 0) {
                throw new DragonflyPullException(DragonflyPullErrorKind.INVALID_REQUEST, "maxRetries must be >= 0");
            }
            configuredGrpcConfig.validate();
        }
    }
}
