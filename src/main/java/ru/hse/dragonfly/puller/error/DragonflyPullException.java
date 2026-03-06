package ru.hse.dragonfly.puller.error;

public final class DragonflyPullException extends Exception {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final DragonflyPullErrorKind kind;

    public DragonflyPullException(DragonflyPullErrorKind errorKind, String message) {
        super(message);
        this.kind = errorKind;
    }

    public DragonflyPullException(DragonflyPullErrorKind errorKind, String message, Throwable cause) {
        super(message, cause);
        this.kind = errorKind;
    }

    public DragonflyPullErrorKind errorKind() {
        return kind;
    }
}
