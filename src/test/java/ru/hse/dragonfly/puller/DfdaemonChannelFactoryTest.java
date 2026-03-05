package ru.hse.dragonfly.puller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ru.hse.dragonfly.puller.error.DragonflyPullException;
import ru.hse.dragonfly.puller.error.ErrorKind;
import org.junit.jupiter.api.Test;

class DfdaemonChannelFactoryTest {

    @Test
    void rejectsBlankAddress() {
        DragonflyPullException ex = assertThrows(
                DragonflyPullException.class,
                () -> DfdaemonChannelFactory.create(" ")
        );
        assertEquals(ErrorKind.INVALID_REQUEST, ex.errorKind());
    }

    @Test
    void rejectsBrokenTcpAddress() {
        DragonflyPullException ex = assertThrows(
                DragonflyPullException.class,
                () -> DfdaemonChannelFactory.create("localhost:")
        );
        assertEquals(ErrorKind.INVALID_REQUEST, ex.errorKind());
    }
}
