package ru.hse.dragonfly.puller.blobpuller;

import java.io.Closeable;

import ru.hse.dragonfly.puller.error.DragonflyPullException;

public interface BlobPullGateway extends Closeable {
    PullResult pull(PullRequest request) throws DragonflyPullException;
}
