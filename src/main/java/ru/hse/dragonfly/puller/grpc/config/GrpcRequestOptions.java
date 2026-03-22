package ru.hse.dragonfly.puller.grpc.config;

public record GrpcRequestOptions(long requestTimeoutMillis, int maxAttempts) {
    private static final long NO_TIMEOUT_MILLIS = -1L;

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
