package hse.ru.dragonfly.puller.error;

public final class DragonflyPullException extends Exception {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final ErrorKind kind;

    public DragonflyPullException(ErrorKind errorKind, String message) {
        super(message);
        this.kind = errorKind;
    }

    public DragonflyPullException(ErrorKind errorKind, String message, Throwable cause) {
        super(message, cause);
        this.kind = errorKind;
    }

    public ErrorKind errorKind() {
        return kind;
    }
}
