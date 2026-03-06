package ru.hse.dragonfly.puller.registry;

import java.nio.file.Path;
import java.util.Objects;

public record RegistryPullRequest(
        String registry,
        String repository,
        String tag,
        String digest,
        RegistryAuth auth,
        Path outputPath
) {
    public RegistryPullRequest {
        if (registry == null || registry.isBlank()) {
            throw new IllegalArgumentException("registry must not be blank");
        }
        if (repository == null || repository.isBlank()) {
            throw new IllegalArgumentException("repository must not be blank");
        }
        auth = auth == null ? RegistryAuth.none() : auth;
        Objects.requireNonNull(outputPath, "outputPath must not be null");
    }
}
