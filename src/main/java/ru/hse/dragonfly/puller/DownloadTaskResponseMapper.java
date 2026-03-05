package ru.hse.dragonfly.puller;

import org.dragonflyoss.api.dfdaemon.v2.DownloadTaskResponse;

public final class DownloadTaskResponseMapper {
    private DownloadTaskResponseMapper() {
    }

    public static boolean isFinished(DownloadTaskResponse response) {
        return response.hasDownloadTaskStartedResponse()
                && response.getDownloadTaskStartedResponse().getIsFinished();
    }
}
