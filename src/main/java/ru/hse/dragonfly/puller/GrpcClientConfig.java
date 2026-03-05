package ru.hse.dragonfly.puller;

import java.time.Duration;

import ru.hse.dragonfly.puller.error.DragonflyPullException;
import ru.hse.dragonfly.puller.error.ErrorKind;

public final class GrpcClientConfig {
    private static final Duration DEFAULT_KEEP_ALIVE_TIME = Duration.ofSeconds(30);
    private static final Duration DEFAULT_KEEP_ALIVE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_INITIAL_RETRY_BACKOFF = Duration.ofMillis(500);
    private static final Duration DEFAULT_MAX_RETRY_BACKOFF = Duration.ofSeconds(5);
    private static final double DEFAULT_RETRY_BACKOFF_MULTIPLIER = 2.0;

    private final Duration keepAliveTime;
    private final Duration keepAliveTimeout;
    private final Duration initialRetryBackoff;
    private final Duration maxRetryBackoff;
    private final double retryBackoffMultiplier;

    private GrpcClientConfig(
            Duration keepAliveTime,
            Duration keepAliveTimeout,
            Duration initialRetryBackoff,
            Duration maxRetryBackoff,
            double retryBackoffMultiplier
    ) {
        this.keepAliveTime = keepAliveTime;
        this.keepAliveTimeout = keepAliveTimeout;
        this.initialRetryBackoff = initialRetryBackoff;
        this.maxRetryBackoff = maxRetryBackoff;
        this.retryBackoffMultiplier = retryBackoffMultiplier;
    }

    public static GrpcClientConfig defaults() {
        return new GrpcClientConfig(
                DEFAULT_KEEP_ALIVE_TIME,
                DEFAULT_KEEP_ALIVE_TIMEOUT,
                DEFAULT_INITIAL_RETRY_BACKOFF,
                DEFAULT_MAX_RETRY_BACKOFF,
                DEFAULT_RETRY_BACKOFF_MULTIPLIER
        );
    }

    public GrpcClientConfig withKeepAliveTime(Duration value) {
        return new GrpcClientConfig(
                value,
                keepAliveTimeout,
                initialRetryBackoff,
                maxRetryBackoff,
                retryBackoffMultiplier
        );
    }

    public GrpcClientConfig withKeepAliveTimeout(Duration value) {
        return new GrpcClientConfig(
                keepAliveTime,
                value,
                initialRetryBackoff,
                maxRetryBackoff,
                retryBackoffMultiplier
        );
    }

    public GrpcClientConfig withInitialRetryBackoff(Duration value) {
        return new GrpcClientConfig(
                keepAliveTime,
                keepAliveTimeout,
                value,
                maxRetryBackoff,
                retryBackoffMultiplier
        );
    }

    public GrpcClientConfig withMaxRetryBackoff(Duration value) {
        return new GrpcClientConfig(
                keepAliveTime,
                keepAliveTimeout,
                initialRetryBackoff,
                value,
                retryBackoffMultiplier
        );
    }

    public GrpcClientConfig withRetryBackoffMultiplier(double value) {
        return new GrpcClientConfig(
                keepAliveTime,
                keepAliveTimeout,
                initialRetryBackoff,
                maxRetryBackoff,
                value
        );
    }

    public void validate() throws DragonflyPullException {
        requirePositive(keepAliveTime, "grpcKeepAliveTime");
        requirePositive(keepAliveTimeout, "grpcKeepAliveTimeout");
        requirePositive(initialRetryBackoff, "grpcInitialRetryBackoff");
        requirePositive(maxRetryBackoff, "grpcMaxRetryBackoff");
        if (maxRetryBackoff.compareTo(initialRetryBackoff) < 0) {
            throw new DragonflyPullException(
                    ErrorKind.INVALID_REQUEST,
                    "grpcMaxRetryBackoff must be >= grpcInitialRetryBackoff"
            );
        }
        if (retryBackoffMultiplier <= 1.0) {
            throw new DragonflyPullException(
                    ErrorKind.INVALID_REQUEST,
                    "grpcRetryBackoffMultiplier must be greater than 1.0"
            );
        }
    }

    public long keepAliveTimeMillis() {
        return keepAliveTime.toMillis();
    }

    public long keepAliveTimeoutMillis() {
        return keepAliveTimeout.toMillis();
    }

    public long initialRetryBackoffMillis() {
        return initialRetryBackoff.toMillis();
    }

    public long maxRetryBackoffMillis() {
        return maxRetryBackoff.toMillis();
    }

    public double retryBackoffMultiplier() {
        return retryBackoffMultiplier;
    }

    private static void requirePositive(Duration value, String fieldName) throws DragonflyPullException {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new DragonflyPullException(ErrorKind.INVALID_REQUEST, fieldName + " must be positive");
        }
    }
}
