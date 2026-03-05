package ru.hse.dragonfly.puller;

import ru.hse.dragonfly.puller.error.DragonflyPullException;
import ru.hse.dragonfly.puller.error.ErrorKind;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DragonflyImagePullerBuilderTest {

    @Test
    void buildFailsWhenAddressIsBlank() {
        DragonflyPullException ex = assertThrows(
                DragonflyPullException.class,
                () -> DragonflyImagePuller.builder().withAddress(" ").build()
        );
        assertEquals(ErrorKind.INVALID_REQUEST, ex.errorKind());
    }

    @Test
    void buildFailsWhenTimeoutIsNotPositive() {
        DragonflyPullException ex = assertThrows(
                DragonflyPullException.class,
                () -> DragonflyImagePuller.builder().withRequestTimeout(Duration.ZERO).build()
        );
        assertEquals(ErrorKind.INVALID_REQUEST, ex.errorKind());
    }

    @Test
    void buildFailsWhenRetriesIsNegative() {
        DragonflyPullException ex = assertThrows(
                DragonflyPullException.class,
                () -> DragonflyImagePuller.builder().withMaxRetries(-1).build()
        );
        assertEquals(ErrorKind.INVALID_REQUEST, ex.errorKind());
    }

    @Test
    void buildWorksWithExplicitValidConfiguration() throws DragonflyPullException, IOException {
        try (DragonflyImagePuller ignored = DragonflyImagePuller.builder()
                .withAddress("localhost:65001")
                .withRequestTimeout(Duration.ofSeconds(5))
                .withMaxRetries(0)
                .build()) {
            // no-op
        }
    }
}
