package ru.hse.dragonfly.puller.blobpuller;

import java.io.Closeable;
import java.io.IOException;

import ru.hse.dragonfly.puller.error.DragonflyPullException;
import ru.hse.dragonfly.puller.grpcdfdaemon.DfdaemonDownloadClient;

public final class BlobPuller implements BlobPullGateway, Closeable {
    private final DfdaemonDownloadClient client;

    public BlobPuller(DfdaemonDownloadClient client) {
        this.client = client;
    }

    @Override
    public PullResult pull(PullRequest request) throws DragonflyPullException {
        return client.pull(request);
    }

    @Override
    public void close() throws IOException {
        client.close();
    }
}
