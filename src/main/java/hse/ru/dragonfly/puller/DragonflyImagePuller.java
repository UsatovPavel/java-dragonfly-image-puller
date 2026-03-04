package hse.ru.dragonfly.puller;

import hse.ru.dragonfly.puller.error.DragonflyPullException;
import hse.ru.dragonfly.puller.error.ErrorKind;
import hse.ru.dragonfly.puller.model.PullRequest;
import hse.ru.dragonfly.puller.model.PullResult;

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;

public final class DragonflyImagePuller implements Closeable {
    private final DfdaemonDownloadClient client;

    private DragonflyImagePuller(DfdaemonDownloadClient client) {
        this.client = client;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static DragonflyImagePuller createDefault() throws DragonflyPullException {
        return builder().build();
    }

    public PullResult pull(PullRequest request) throws DragonflyPullException {
        return client.pull(request);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }

    public static final class Builder {
        private static final String DEFAULT_ADDRESS = System.getenv()
                .getOrDefault("DFDAEMON_ADDR", "unix:///var/run/dragonfly/dfdaemon.sock");
        private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(120);
        private static final int DEFAULT_MAX_RETRIES = 1;

        private String configuredAddress = DEFAULT_ADDRESS;
        private Duration configuredRequestTimeout = DEFAULT_REQUEST_TIMEOUT;
        private int configuredMaxRetries = DEFAULT_MAX_RETRIES;

        public Builder address(String value) {
            return withAddress(value);
        }

        public Builder withAddress(String value) {
            this.configuredAddress = value;
            return this;
        }

        public Builder requestTimeout(Duration value) {
            return withRequestTimeout(value);
        }

        public Builder withRequestTimeout(Duration value) {
            this.configuredRequestTimeout = value;
            return this;
        }

        public Builder maxRetries(int value) {
            return withMaxRetries(value);
        }

        public Builder withMaxRetries(int value) {
            this.configuredMaxRetries = value;
            return this;
        }

        public DragonflyImagePuller build() throws DragonflyPullException {
            validate();
            DfdaemonDownloadClient client = new DfdaemonDownloadClient(
                    configuredAddress,
                    configuredRequestTimeout,
                    configuredMaxRetries
            );
            return new DragonflyImagePuller(client);
        }

        private void validate() throws DragonflyPullException {
            if (configuredAddress == null || configuredAddress.isBlank()) {
                throw new DragonflyPullException(ErrorKind.INVALID_REQUEST, "address must not be blank");
            }
            if (configuredRequestTimeout == null
                    || configuredRequestTimeout.isZero()
                    || configuredRequestTimeout.isNegative()) {
                throw new DragonflyPullException(ErrorKind.INVALID_REQUEST, "requestTimeout must be positive");
            }
            if (configuredMaxRetries < 0) {
                throw new DragonflyPullException(ErrorKind.INVALID_REQUEST, "maxRetries must be >= 0");
            }
        }
    }
}
