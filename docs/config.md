## Config module

### Purpose
Aggregate and validate settings of `DragonflyImagePuller` (address, timeout, retries, gRPC keepalive/retry backoff) through `Builder` and `GrpcClientConfig`.

### Public configuration API
- `DragonflyImagePuller.builder().withAddress(String)`
- `withRequestTimeout(Duration)`
- `withMaxRetries(int)`
- `withGrpcKeepAliveTime(Duration)`
- `withGrpcKeepAliveTimeout(Duration)`
- `withGrpcInitialRetryBackoff(Duration)`
- `withGrpcMaxRetryBackoff(Duration)`
- `withGrpcRetryBackoffMultiplier(double)`

### Minimal example
```java
try (DragonflyImagePuller puller = DragonflyImagePuller.builder()
        .withAddress("unix:///var/run/dragonfly/dfdaemon.sock")
        .build()) {
    // pull...
}
```

### Optional tuned example
```java
try (DragonflyImagePuller puller = DragonflyImagePuller.builder()
        .withAddress("dfscheduler-host:65001")
        .withRequestTimeout(java.time.Duration.ofSeconds(30))
        .withMaxRetries(2)
        .withGrpcKeepAliveTime(java.time.Duration.ofSeconds(20))
        .withGrpcKeepAliveTimeout(java.time.Duration.ofSeconds(10))
        .withGrpcInitialRetryBackoff(java.time.Duration.ofMillis(300))
        .withGrpcMaxRetryBackoff(java.time.Duration.ofSeconds(3))
        .withGrpcRetryBackoffMultiplier(2.0)
        .build()) {
    // pull...
}
```

### Defaults
- `address`: env `DFDAEMON_ADDR` or `unix:///var/run/dragonfly/dfdaemon.sock`
- `requestTimeout`: infinite (no deadline) when `withRequestTimeout(...)` is not set
- `maxRetries`: `1` (effective attempts inside gRPC policy: `retries + 1`)
- `grpcKeepAliveTime`: `PT30S`
- `grpcKeepAliveTimeout`: `PT10S`
- `grpcInitialRetryBackoff`: `PT0.5S`
- `grpcMaxRetryBackoff`: `PT5S`
- `grpcRetryBackoffMultiplier`: `2.0`

### Validation rules
- `address` must not be blank.
- `requestTimeout` may be omitted; if set, it must be positive.
- `maxRetries` must be `>= 0`.
- `grpcKeepAliveTime`, `grpcKeepAliveTimeout`, `grpcInitialRetryBackoff`, `grpcMaxRetryBackoff` must be positive.
- `grpcMaxRetryBackoff >= grpcInitialRetryBackoff`.
- `grpcRetryBackoffMultiplier > 1.0`.

### Retry behavior
- gRPC retry policy is enabled when effective `maxAttempts >= 2`.
- Retryable gRPC statuses: `UNAVAILABLE`, `RESOURCE_EXHAUSTED`, `DEADLINE_EXCEEDED`.
- When retries are effectively disabled, channel builder uses `disableRetry()`.

### Address formats
- Unix socket: `unix:///var/run/dragonfly/dfdaemon.sock`
- TCP: `host:port`
- TCP mode uses `usePlaintext()` (no TLS); intended only for trusted/internal network.

### Notes
- Все validation ошибки конфигурации возвращаются как `DragonflyPullException` с `errorKind=INVALID_REQUEST`.
- Runtime gRPC/IO проблемы маппятся в `TIMEOUT`, `UNAVAILABLE`, `IO`, `INTERNAL`.
