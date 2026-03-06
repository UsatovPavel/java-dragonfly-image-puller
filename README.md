# java-dragonfly-image-puller

Java library for downloading registry blobs through Dragonfly `dfdaemon` over gRPC.

## Publishing

The library is published to GitHub Packages.

- Group: `hse.ru`
- Artifact: `java-dragonfly-image-puller`

To consume from GitHub Packages, add the GitHub Maven repository with credentials and then add dependency `hse.ru:java-dragonfly-image-puller:<version>`.

## Requirements

- Java 23
- Running Dragonfly `dfdaemon` endpoint:
  - `unix:///var/run/dragonfly/dfdaemon.sock`, or
  - `host:port`

For local integration tests:

- Linux environment
- At least 6000 MB RAM
- Prepared Dragonfly environment (install via scripts/ci-dragonfly-setup.sh)

## Public API

Main entry point is `DragonflyImagePuller`.

Input is `RegistryPullRequest`:

- `registry` (host or URL)
- `repository`
- `tag` or `digest` (digest is required for pull-by-digest flow)
- `auth` (`RegistryAuth`)
- `outputPath`

Authentication options in `RegistryAuth`:

- basic auth (`basicAuthUsername`, `basicAuthPassword`)
- jwt token (`jwtToken`)

## Quick Start

```java
import java.nio.file.Path;

import ru.hse.dragonfly.puller.DragonflyImagePuller;
import ru.hse.dragonfly.puller.registry.RegistryAuth;
import ru.hse.dragonfly.puller.registry.RegistryPullRequest;

try (DragonflyImagePuller puller = DragonflyImagePuller.builder()
        .withAddress("unix:///var/run/dragonfly/dfdaemon.sock")
        .build()) {
    RegistryPullRequest request = new RegistryPullRequest(
            "registry.example.com",
            "repo/image",
            null,
            "sha256:your-digest",
            RegistryAuth.none(),
            Path.of("/tmp/blob.bin")
    );
    puller.pull(request);
}
```

## Builder Configuration

`DragonflyImagePuller.builder()` supports:

- `withAddress(String)`
- `withRequestTimeout(Duration)`
- `withMaxRetries(int)`
- `withGrpcKeepAliveTime(Duration)`
- `withGrpcKeepAliveTimeout(Duration)`
- `withGrpcInitialRetryBackoff(Duration)`
- `withGrpcMaxRetryBackoff(Duration)`
- `withGrpcRetryBackoffMultiplier(double)`

## Error Types

General pull errors (`DragonflyPullErrorKind`):

- `INVALID_REQUEST`
- `TIMEOUT`
- `UNAVAILABLE`
- `IO`
- `INTERNAL`

Registry-specific errors (`RegistryPullErrorKind`):

- `UNAUTHORIZED`
- `FORBIDDEN`
- `NOT_FOUND`
- `TLS_ERROR`

## Development

Run quality checks:

```bash
./gradlew check
```

Run tests:

```bash
./gradlew test
```

Run local integration test (when Dragonfly is available):

```bash
DFDAEMON_ADDR=unix:///var/run/dragonfly/dfdaemon.sock ./gradlew --no-daemon test --tests ru.hse.dragonfly.puller.BlobPullerLocalIntegrationTest
```
