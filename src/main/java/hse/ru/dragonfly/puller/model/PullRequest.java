package hse.ru.dragonfly.puller.model;

import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

public record PullRequest(
        String blobUrl,
        String digest,
        Path outputPath,
        Map<String, String> headers
) {
    public PullRequest {
        if (blobUrl == null || blobUrl.isBlank()) {
            throw new IllegalArgumentException("blobUrl must not be blank");
        }
        if (digest == null || digest.isBlank()) {
            throw new IllegalArgumentException("digest must not be blank");
        }
        Objects.requireNonNull(outputPath, "outputPath must not be null");
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
