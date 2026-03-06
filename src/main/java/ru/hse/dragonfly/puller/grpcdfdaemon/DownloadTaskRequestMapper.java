package ru.hse.dragonfly.puller.grpcdfdaemon;

import ru.hse.dragonfly.puller.blobpuller.PullRequest;
import org.dragonflyoss.api.common.v2.Download;
import org.dragonflyoss.api.common.v2.Priority;
import org.dragonflyoss.api.common.v2.TaskType;
import org.dragonflyoss.api.dfdaemon.v2.DownloadTaskRequest;

public final class DownloadTaskRequestMapper {
    private DownloadTaskRequestMapper() {
    }

    public static DownloadTaskRequest toProto(PullRequest request) {
        Download.Builder download = Download.newBuilder()
                .setUrl(request.blobUrl())
                .setOutputPath(request.outputPath().toString())
                .setType(TaskType.STANDARD)
                .setPriority(Priority.LEVEL0);

        if (!request.digest().isBlank()) {
            download.setDigest(request.digest());
        }
        if (!request.headers().isEmpty()) {
            download.putAllRequestHeader(request.headers());
        }

        return DownloadTaskRequest.newBuilder()
                .setDownload(download.build())
                .build();
    }
}
