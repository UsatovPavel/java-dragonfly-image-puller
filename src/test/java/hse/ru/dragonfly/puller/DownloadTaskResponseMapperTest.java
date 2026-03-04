package hse.ru.dragonfly.puller;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.dragonflyoss.api.dfdaemon.v2.DownloadTaskResponse;
import org.dragonflyoss.api.dfdaemon.v2.DownloadTaskStartedResponse;
import org.junit.jupiter.api.Test;

class DownloadTaskResponseMapperTest {

    @Test
    void detectsFinishedResponse() {
        DownloadTaskResponse response = DownloadTaskResponse.newBuilder()
                .setDownloadTaskStartedResponse(
                        DownloadTaskStartedResponse.newBuilder().setIsFinished(true).build()
                )
                .build();
        assertTrue(DownloadTaskResponseMapper.isFinished(response));
    }

    @Test
    void detectsNonFinishedResponse() {
        DownloadTaskResponse response = DownloadTaskResponse.newBuilder().build();
        assertFalse(DownloadTaskResponseMapper.isFinished(response));
    }
}
