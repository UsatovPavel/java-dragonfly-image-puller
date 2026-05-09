package ru.hse.dragonfly.puller.grpc.mapper;

import java.util.List;
import java.util.Map;

public final class GrpcRetryServiceConfigMapper {
    private static final long ZERO_NANOS_PART = 0L;

    private GrpcRetryServiceConfigMapper() {
    }

    public static Map<String, ?> buildDownloadTaskServiceConfig(
            int maxAttempts,
            long initialRetryBackoffMillis,
            long maxRetryBackoffMillis,
            double retryBackoffMultiplier
    ) {
        Map<String, ?> retryPolicy = Map.of(
                "maxAttempts", (double) maxAttempts,
                "initialBackoff", toGrpcDuration(initialRetryBackoffMillis),
                "maxBackoff", toGrpcDuration(maxRetryBackoffMillis),
                "backoffMultiplier", retryBackoffMultiplier,
                "retryableStatusCodes", List.of("UNAVAILABLE", "RESOURCE_EXHAUSTED", "DEADLINE_EXCEEDED")
        );
        Map<String, ?> methodConfig = Map.of(
                "name", List.of(Map.of()),
                "retryPolicy", retryPolicy
        );
        return Map.of("methodConfig", List.of(methodConfig));
    }

    private static String toGrpcDuration(long millis) {
        long secondsPart = millis / 1000L;
        long nanosPart = (millis % 1000L) * 1_000_000L;
        if (nanosPart == ZERO_NANOS_PART) {
            return secondsPart + "s";
        }
        return secondsPart + "." + String.format("%09d", nanosPart) + "s";
    }
}
