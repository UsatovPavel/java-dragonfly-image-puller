package ru.hse.dragonfly.puller.grpc.config;

public record GrpcRequestOptions(long requestTimeoutMillis, int maxAttempts) {
    private static final long NO_TIMEOUT_MILLIS = -1L;
    private static final int DEFAULT_MAX_ATTEMPTS = 1;

    public static GrpcRequestOptions defaultOptions() {
        return new GrpcRequestOptions(NO_TIMEOUT_MILLIS, DEFAULT_MAX_ATTEMPTS);
    }
    
    public boolean isTimeoutEnabled() {
        return requestTimeoutMillis != NO_TIMEOUT_MILLIS;
    }

    public String timeoutLogValue() {
        return isTimeoutEnabled() ? String.valueOf(requestTimeoutMillis) : "infinite";
    }

    public static long noTimeoutMillis() {
        return NO_TIMEOUT_MILLIS;
    }
}
