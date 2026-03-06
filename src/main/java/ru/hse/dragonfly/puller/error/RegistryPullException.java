package ru.hse.dragonfly.puller.error;
import java.io.Serial;
public final class RegistryPullException extends Exception {
    @Serial
    private static final long serialVersionUID = 1L;

    private final RegistryPullErrorKind kind;

    public RegistryPullException(RegistryPullErrorKind errorKind, String message) {
        super(message);
        this.kind = errorKind;
    }

    public RegistryPullException(RegistryPullErrorKind errorKind, String message, Throwable cause) {
        super(message, cause);
        this.kind = errorKind;
    }

    public RegistryPullErrorKind errorKind() {
        return kind;
    }
}
