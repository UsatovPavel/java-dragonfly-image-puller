package hse.ru.dragonfly.puller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;

import hse.ru.dragonfly.puller.model.PullRequest;
import org.junit.jupiter.api.Test;

class DownloadTaskRequestMapperTest {

    @Test
    void mapsRequestToProto() {
        PullRequest request = new PullRequest(
                "https://registry.example.com/v2/a/blobs/sha256:abc",
                "sha256:abc",
                Path.of("/tmp/blob"),
                Map.of("Authorization", "Bearer token")
        );

        var proto = DownloadTaskRequestMapper.toProto(request);
        assertEquals("https://registry.example.com/v2/a/blobs/sha256:abc", proto.getDownload().getUrl());
        assertEquals("/tmp/blob", proto.getDownload().getOutputPath());
        assertEquals("sha256:abc", proto.getDownload().getDigest());
        assertEquals("Bearer token", proto.getDownload().getRequestHeaderOrThrow("Authorization"));
        assertTrue(proto.getDownload().hasDigest());
    }
}
