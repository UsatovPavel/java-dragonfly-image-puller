package ru.hse.dragonfly.puller.blobpuller;

import java.nio.file.Path;
import java.util.Objects;

public record PullResult(Path path) {
    public PullResult {
        Objects.requireNonNull(path, "path must not be null");
    }
}
