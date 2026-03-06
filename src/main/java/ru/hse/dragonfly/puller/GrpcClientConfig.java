package ru.hse.dragonfly.puller;

import java.time.Duration;

import ru.hse.dragonfly.puller.error.DragonflyPullErrorKind;
import ru.hse.dragonfly.puller.error.DragonflyPullException;

public final class GrpcClientConfig {
    private static final Duration DEFAULT_KEEP_ALIVE_TIME = Duration.ofSeconds(30);
    private static final Duration DEFAULT_KEEP_ALIVE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_INITIAL_RETRY_BACKOFF = Duration.ofMillis(500);
    private static final Duration DEFAULT_MAX_RETRY_BACKOFF = Duration.ofSeconds(5);
    private static final double DEFAULT_RETRY_BACKOFF_MULTIPLIER = 2.0;
    private static final double MIN_RETRY_BACKOFF_MULTIPLIER_EXCLUSIVE = 1.0;

    private final Duration keepAliveTime;
    private final Duration keepAliveTimeout;
    private final Duration initialRetryBackoff;
    private final Duration maxRetryBackoff;
    private final double retryBackoffMultiplierValue;

    private GrpcClientConfig(
            Duration keepAliveTime,
            Duration keepAliveTimeout,
            Duration initialRetryBackoff,
            Duration maxRetryBackoff,
            double retryBackoffMultiplierValue
    ) {
        this.keepAliveTime = keepAliveTime;
        this.keepAliveTimeout = keepAliveTimeout;
        this.initialRetryBackoff = initialRetryBackoff;
        this.maxRetryBackoff = maxRetryBackoff;
        this.retryBackoffMultiplierValue = retryBackoffMultiplierValue;
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
                retryBackoffMultiplierValue
        );
    }

    public GrpcClientConfig withKeepAliveTimeout(Duration value) {
        return new GrpcClientConfig(
                keepAliveTime,
                value,
                initialRetryBackoff,
                maxRetryBackoff,
                retryBackoffMultiplierValue
        );
    }

    public GrpcClientConfig withInitialRetryBackoff(Duration value) {
        return new GrpcClientConfig(
                keepAliveTime,
                keepAliveTimeout,
                value,
                maxRetryBackoff,
                retryBackoffMultiplierValue
        );
    }

    public GrpcClientConfig withMaxRetryBackoff(Duration value) {
        return new GrpcClientConfig(
                keepAliveTime,
                keepAliveTimeout,
                initialRetryBackoff,
                value,
                retryBackoffMultiplierValue
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
                    DragonflyPullErrorKind.INVALID_REQUEST,
                    "grpcMaxRetryBackoff must be >= grpcInitialRetryBackoff"
            );
        }
        if (retryBackoffMultiplierValue <= MIN_RETRY_BACKOFF_MULTIPLIER_EXCLUSIVE) {
            throw new DragonflyPullException(
                    DragonflyPullErrorKind.INVALID_REQUEST,
                    "grpcRetryBackoffMultiplier must be greater than "
                            + MIN_RETRY_BACKOFF_MULTIPLIER_EXCLUSIVE
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
        return retryBackoffMultiplierValue;
    }

    private static void requirePositive(Duration value, String fieldName) throws DragonflyPullException {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new DragonflyPullException(
                    DragonflyPullErrorKind.INVALID_REQUEST,
                    fieldName + " must be positive"
            );
        }
    }
}
