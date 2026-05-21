package ru.hse.dragonfly.puller.grpc.config;

import java.time.Duration;

import ru.hse.dragonfly.puller.error.DragonflyPullErrorKind;
import ru.hse.dragonfly.puller.error.DragonflyPullException;

public final class GrpcRequestOptionsNormalizer {
    private static final int DEFAULT_MAX_ATTEMPTS = 2;

    private GrpcRequestOptionsNormalizer() {
    }

    public static GrpcRequestOptions normalize(Duration requestTimeout, Integer maxRetries) throws DragonflyPullException {
        return new GrpcRequestOptions(
                normalizeTimeoutMillis(requestTimeout),
                normalizeMaxAttempts(maxRetries)
        );
    }

    private static long normalizeTimeoutMillis(Duration requestTimeout) throws DragonflyPullException {
        if (requestTimeout == null) {
            return GrpcRequestOptions.noTimeoutMillis();
        }
        if (requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new DragonflyPullException(
                    DragonflyPullErrorKind.INVALID_REQUEST,
                    "requestTimeout must be positive"
            );
        }
        return requestTimeout.toMillis();
    }

    private static int normalizeMaxAttempts(Integer maxRetries) throws DragonflyPullException {
        if (maxRetries == null) {
            return DEFAULT_MAX_ATTEMPTS;
        }
        if (maxRetries < 0) {
            throw new DragonflyPullException(DragonflyPullErrorKind.INVALID_REQUEST, "maxRetries must be >= 0");
        }
        return maxRetries + 1;
    }
}
