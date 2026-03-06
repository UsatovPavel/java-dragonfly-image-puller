package ru.hse.dragonfly.puller.grpcdfdaemon;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.time.Duration;

import ru.hse.dragonfly.puller.GrpcClientConfig;
import ru.hse.dragonfly.puller.error.DragonflyPullErrorKind;
import ru.hse.dragonfly.puller.error.DragonflyPullException;
import ru.hse.dragonfly.puller.blobpuller.PullRequest;
import ru.hse.dragonfly.puller.blobpuller.PullResult;
import io.grpc.Context;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.dragonflyoss.api.dfdaemon.v2.DfdaemonDownloadGrpc;
import org.dragonflyoss.api.dfdaemon.v2.DownloadTaskRequest;
import org.dragonflyoss.api.dfdaemon.v2.DownloadTaskResponse;

public final class DfdaemonDownloadClient implements Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(DfdaemonDownloadClient.class);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(120);
    private static final int DEFAULT_MAX_ATTEMPTS = 2;
    private static final int MIN_RETRY_ATTEMPTS = 2;
    private static final long ZERO_NANOS_PART = 0L;

    private final ManagedChannel channel;
    private final DfdaemonDownloadGrpc.DfdaemonDownloadStub asyncStub;
    private final long requestTimeoutMillis;
    private final int maxAttempts;
    private final long initialRetryBackoffMillis;
    private final long maxRetryBackoffMillis;
    private final double retryBackoffMultiplier;

    public DfdaemonDownloadClient(String dfdaemonAddress) throws DragonflyPullException {
        this(dfdaemonAddress, null, null, null);
    }

    public DfdaemonDownloadClient(
            String dfdaemonAddress,
            java.time.Duration requestTimeout,
            Integer maxRetries,
            GrpcClientConfig grpcClientConfig
    ) throws DragonflyPullException {
        this.requestTimeoutMillis = normalizeTimeoutMillis(requestTimeout);
        this.maxAttempts = normalizeMaxAttempts(maxRetries);
        GrpcClientConfig effectiveGrpcConfig = grpcClientConfig == null ? GrpcClientConfig.defaults() : grpcClientConfig;
        effectiveGrpcConfig.validate();
        long keepAliveTimeMillis = effectiveGrpcConfig.keepAliveTimeMillis();
        long keepAliveTimeoutMillis = effectiveGrpcConfig.keepAliveTimeoutMillis();
        this.initialRetryBackoffMillis = effectiveGrpcConfig.initialRetryBackoffMillis();
        this.maxRetryBackoffMillis = effectiveGrpcConfig.maxRetryBackoffMillis();
        this.retryBackoffMultiplier = effectiveGrpcConfig.retryBackoffMultiplier();
        if (maxRetryBackoffMillis < initialRetryBackoffMillis) {
            LOG.warn(
                    "invalid retry backoff configuration: initialRetryBackoffMs={} maxRetryBackoffMs={}",
                    initialRetryBackoffMillis,
                    maxRetryBackoffMillis
            );
            throw new DragonflyPullException(
                    DragonflyPullErrorKind.INVALID_REQUEST,
                    "maxRetryBackoff must be greater than or equal to initialRetryBackoff"
            );
        }
        ManagedChannelBuilder<?> builder = DfdaemonChannelFactory.createBuilder(dfdaemonAddress)
                .keepAliveTime(keepAliveTimeMillis, TimeUnit.MILLISECONDS)
                .keepAliveTimeout(keepAliveTimeoutMillis, TimeUnit.MILLISECONDS);
        if (maxAttempts >= MIN_RETRY_ATTEMPTS) {
            builder = builder.enableRetry().defaultServiceConfig(buildRetryServiceConfig(
                    maxAttempts,
                    initialRetryBackoffMillis,
                    maxRetryBackoffMillis,
                    this.retryBackoffMultiplier
            ));
        } else {
            builder = builder.disableRetry();
        }
        this.channel = builder.build();
        this.asyncStub = DfdaemonDownloadGrpc.newStub(channel);
        LOG.info(
                "dfdaemon client initialized: address={} requestTimeoutMs={} keepAliveTimeMs={} "
                        + "keepAliveTimeoutMs={} maxAttempts={} initialRetryBackoffMs={} maxRetryBackoffMs={} "
                        + "retryBackoffMultiplier={}",
                dfdaemonAddress,
                requestTimeoutMillis,
                keepAliveTimeMillis,
                keepAliveTimeoutMillis,
                maxAttempts,
                initialRetryBackoffMillis,
                maxRetryBackoffMillis,
                this.retryBackoffMultiplier
        );
    }

    public PullResult pull(PullRequest request) throws DragonflyPullException {
        DownloadTaskRequest protoRequest = DownloadTaskRequestMapper.toProto(request);
        LOG.info(
                "starting download task: outputPath={} timeoutMs={} maxAttempts={}",
                request.outputPath(),
                requestTimeoutMillis,
                maxAttempts
        );
        CountDownLatch done = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean(false);
        AtomicReference<StatusRuntimeException> grpcError = new AtomicReference<>();
        AtomicReference<Throwable> internalError = new AtomicReference<>();
        try (Context.CancellableContext cancellableContext = Context.current().withCancellation()) {
            cancellableContext.run(() -> asyncStub
                    .withDeadlineAfter(requestTimeoutMillis, TimeUnit.MILLISECONDS)
                    .downloadTask(protoRequest, new StreamObserver<>() {
                        @Override
                        public void onNext(DownloadTaskResponse response) {
                            if (DownloadTaskResponseMapper.isFinished(response)) {
                                finished.set(true);
                            }
                        }

                        @Override
                        public void onError(Throwable throwable) {
                            if (throwable instanceof StatusRuntimeException statusRuntimeException) {
                                grpcError.set(statusRuntimeException);
                            } else {
                                internalError.set(throwable);
                            }
                            done.countDown();
                        }

                        @Override
                        public void onCompleted() {
                            done.countDown();
                        }
                    }));

            boolean completed = done.await(requestTimeoutMillis + 1000L, TimeUnit.MILLISECONDS);
            if (!completed) {
                LOG.error("download task did not complete in expected time: timeoutMs={}", requestTimeoutMillis);
                throw new DragonflyPullException(DragonflyPullErrorKind.TIMEOUT, "download task exceeded timeout");
            }
            cancellableContext.cancel(null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.error("download task interrupted while waiting for completion", ex);
            throw new DragonflyPullException(DragonflyPullErrorKind.INTERNAL, "download task interrupted", ex);
        }

        StatusRuntimeException grpcFailure = grpcError.get();
        if (grpcFailure != null) {
            DragonflyPullErrorKind kind = mapError(grpcFailure.getStatus().getCode());
            LOG.error("download task failed: grpcStatus={} mappedErrorKind={}", grpcFailure.getStatus(), kind, grpcFailure);
            throw new DragonflyPullException(kind, "download task failed: " + grpcFailure.getStatus(), grpcFailure);
        }
        Throwable internalFailure = internalError.get();
        if (internalFailure != null) {
            LOG.error("download task failed with unexpected internal error", internalFailure);
            throw new DragonflyPullException(
                    DragonflyPullErrorKind.INTERNAL,
                    "download task failed with internal error",
                    internalFailure
            );
        }
        if (!finished.get()) {
            LOG.warn("download task stream completed without finished marker");
        }
        if (!Files.exists(request.outputPath())) {
            LOG.error("download task finished but output file missing: outputPath={}", request.outputPath());
            throw new DragonflyPullException(
                    DragonflyPullErrorKind.IO,
                    "dfdaemon completed without output file: " + request.outputPath()
            );
        }
        LOG.info("download task completed: outputPath={}", request.outputPath());
        return new PullResult(request.outputPath());
    }

    @Override
    public void close() throws IOException {
        try {
            LOG.info("shutting down dfdaemon channel");
            boolean terminated = channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
            if (!terminated) {
                LOG.warn("dfdaemon channel did not terminate in time, forcing shutdownNow");
                channel.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            LOG.error("dfdaemon channel shutdown interrupted", ex);
            throw new IOException("channel shutdown interrupted", ex);
        }
    }

    private static DragonflyPullErrorKind mapError(Status.Code code) {
        return switch (code) {
            case DEADLINE_EXCEEDED -> DragonflyPullErrorKind.TIMEOUT;
            case UNAVAILABLE, RESOURCE_EXHAUSTED -> DragonflyPullErrorKind.UNAVAILABLE;
            case INVALID_ARGUMENT -> DragonflyPullErrorKind.INVALID_REQUEST;
            default -> DragonflyPullErrorKind.INTERNAL;
        };
    }

    private static long normalizeTimeoutMillis(java.time.Duration requestTimeout) throws DragonflyPullException {
        java.time.Duration effective = requestTimeout == null ? DEFAULT_REQUEST_TIMEOUT : requestTimeout;
        if (effective.isZero() || effective.isNegative()) {
            LOG.warn("invalid requestTimeout provided: requestTimeout={}", requestTimeout);
            throw new DragonflyPullException(
                    DragonflyPullErrorKind.INVALID_REQUEST,
                    "requestTimeout must be positive"
            );
        }
        return effective.toMillis();
    }

    private static int normalizeMaxAttempts(Integer maxRetries) throws DragonflyPullException {
        if (maxRetries == null) {
            return DEFAULT_MAX_ATTEMPTS;
        }
        if (maxRetries < 0) {
            LOG.warn("invalid maxRetries provided: maxRetries={}", maxRetries);
            throw new DragonflyPullException(DragonflyPullErrorKind.INVALID_REQUEST, "maxRetries must be >= 0");
        }
        return maxRetries + 1;
    }

    private static Map<String, ?> buildRetryServiceConfig(
            int maxAttempts,
            long initialRetryBackoffMillis,
            long maxRetryBackoffMillis,
            double retryBackoffMultiplier
    ) {
        Map<String, ?> retryPolicy = Map.of(
                "maxAttempts", (double) maxAttempts,
                "initialBackoff", toGrpcDuration(initialRetryBackoffMillis),
                "maxBackoff", toGrpcDuration(maxRetryBackoffMillis),
                "backoffMultiplier", retryBackoffMultiplier,
                "retryableStatusCodes", List.of("UNAVAILABLE", "RESOURCE_EXHAUSTED", "DEADLINE_EXCEEDED")
        );
        Map<String, ?> methodConfig = Map.of(
                "name", List.of(Map.of("service", "dfdaemon.v2.DfdaemonDownload", "method", "DownloadTask")),
                "retryPolicy", retryPolicy
        );
        return Map.of("methodConfig", List.of(methodConfig));
    }

    private static String toGrpcDuration(long millis) {
        long secondsPart = millis / 1000L;
        long nanosPart = (millis % 1000L) * 1_000_000L;
        if (nanosPart == ZERO_NANOS_PART) {
            return secondsPart + "s";
        }
        return secondsPart + "." + String.format("%09d", nanosPart) + "s";
    }
}
