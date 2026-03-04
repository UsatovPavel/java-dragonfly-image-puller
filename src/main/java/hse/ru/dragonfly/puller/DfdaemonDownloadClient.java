package hse.ru.dragonfly.puller;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;

import hse.ru.dragonfly.puller.error.DragonflyPullException;
import hse.ru.dragonfly.puller.error.ErrorKind;
import hse.ru.dragonfly.puller.model.PullRequest;
import hse.ru.dragonfly.puller.model.PullResult;
import io.grpc.Deadline;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.dragonflyoss.api.dfdaemon.v2.DfdaemonDownloadGrpc;
import org.dragonflyoss.api.dfdaemon.v2.DownloadTaskRequest;
import org.dragonflyoss.api.dfdaemon.v2.DownloadTaskResponse;

public final class DfdaemonDownloadClient implements Closeable {
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(120);
    private static final int DEFAULT_MAX_ATTEMPTS = 2;
    private static final EnumSet<Status.Code> RETRYABLE_CODES = EnumSet.of(
            Status.Code.UNAVAILABLE,
            Status.Code.DEADLINE_EXCEEDED,
            Status.Code.RESOURCE_EXHAUSTED
    );

    private final ManagedChannel channel;
    private final DfdaemonDownloadGrpc.DfdaemonDownloadBlockingStub stub;
    private final long requestTimeoutMillis;
    private final int maxAttempts;

    public DfdaemonDownloadClient(String dfdaemonAddress) throws DragonflyPullException {
        this(dfdaemonAddress, null, null);
    }

    public DfdaemonDownloadClient(
            String dfdaemonAddress,
            Duration requestTimeout,
            Integer maxRetries
    ) throws DragonflyPullException {
        this.channel = DfdaemonChannelFactory.create(dfdaemonAddress);
        this.stub = DfdaemonDownloadGrpc.newBlockingStub(channel);
        this.requestTimeoutMillis = normalizeTimeoutMillis(requestTimeout);
        this.maxAttempts = normalizeMaxAttempts(maxRetries);
    }

    public PullResult pull(PullRequest request) throws DragonflyPullException {
        DownloadTaskRequest protoRequest = DownloadTaskRequestMapper.toProto(request);
        DragonflyPullException lastError = null;

        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            try {
                Iterator<DownloadTaskResponse> it = stub
                        .withDeadline(Deadline.after(requestTimeoutMillis, TimeUnit.MILLISECONDS))
                        .downloadTask(protoRequest);
                while (it.hasNext()) {
                    DownloadTaskResponse response = it.next();
                    boolean finished = DownloadTaskResponseMapper.isFinished(response);
                    if (finished) {
                        break;
                    }
                }
                if (!Files.exists(request.outputPath())) {
                    throw new DragonflyPullException(
                            ErrorKind.IO,
                            "dfdaemon completed without output file: " + request.outputPath()
                    );
                }
                return new PullResult(request.outputPath());
            } catch (StatusRuntimeException ex) {
                ErrorKind kind = mapError(ex.getStatus().getCode());
                lastError = new DragonflyPullException(kind, "download task failed: " + ex.getStatus(), ex);
                boolean hasMoreAttempts = attempt < maxAttempts - 1;
                if (!(isRetryable(ex) && hasMoreAttempts)) {
                    throw lastError;
                }
                sleepBeforeRetry(attempt);
            }
        }

        throw lastError != null
                ? lastError
                : new DragonflyPullException(ErrorKind.INTERNAL, "download failed without detailed status");
    }

    @Override
    public void close() throws IOException {
        try {
            channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IOException("channel shutdown interrupted", ex);
        }
    }

    private static boolean isRetryable(StatusRuntimeException ex) {
        return RETRYABLE_CODES.contains(ex.getStatus().getCode());
    }

    private static ErrorKind mapError(Status.Code code) {
        return switch (code) {
            case DEADLINE_EXCEEDED -> ErrorKind.TIMEOUT;
            case UNAVAILABLE, RESOURCE_EXHAUSTED -> ErrorKind.UNAVAILABLE;
            case INVALID_ARGUMENT -> ErrorKind.INVALID_REQUEST;
            default -> ErrorKind.INTERNAL;
        };
    }

    private static long normalizeTimeoutMillis(Duration requestTimeout) throws DragonflyPullException {
        Duration effective = requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
        if (effective.isZero() || effective.isNegative()) {
            throw new DragonflyPullException(ErrorKind.INVALID_REQUEST, "requestTimeout must be positive");
        }
        return effective.toMillis();
    }

    private static int normalizeMaxAttempts(Integer maxRetries) throws DragonflyPullException {
        if (maxRetries == null) {
            return DEFAULT_MAX_ATTEMPTS;
        }
        if (maxRetries < 0) {
            throw new DragonflyPullException(ErrorKind.INVALID_REQUEST, "maxRetries must be >= 0");
        }
        return maxRetries + 1;
    }

    private static void sleepBeforeRetry(int attempt) throws DragonflyPullException {
        try {
            Thread.sleep(500L * (attempt + 1));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new DragonflyPullException(ErrorKind.INTERNAL, "interrupted during retry backoff", ex);
        }
    }
}
