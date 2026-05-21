package ru.hse.dragonfly.puller.blobpuller;

import java.io.Closeable;
import java.util.concurrent.CompletableFuture;
import org.jetbrains.annotations.NotNull;

public interface BlobPullGateway extends Closeable {
    @NotNull CompletableFuture<PullResult> pull(@NotNull PullRequest request);
}
