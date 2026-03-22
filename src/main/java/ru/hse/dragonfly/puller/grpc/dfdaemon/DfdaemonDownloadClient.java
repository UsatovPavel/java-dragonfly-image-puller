package ru.hse.dragonfly.puller.grpc.dfdaemon;

import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import ru.hse.dragonfly.puller.grpc.config.GrpcClientConfig;
import ru.hse.dragonfly.puller.grpc.config.GrpcRequestOptions;
import ru.hse.dragonfly.puller.grpc.config.GrpcRequestOptionsNormalizer;
import ru.hse.dragonfly.puller.error.DragonflyPullErrorKind;
import ru.hse.dragonfly.puller.error.DragonflyPullException;
import ru.hse.dragonfly.puller.blobpuller.BlobPullGateway;
import ru.hse.dragonfly.puller.blobpuller.PullRequest;
import ru.hse.dragonfly.puller.blobpuller.PullResult;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jetbrains.annotations.NotNull;
import org.dragonflyoss.api.dfdaemon.v2.DfdaemonDownloadGrpc;
import org.dragonflyoss.api.dfdaemon.v2.DownloadTaskRequest;
import org.dragonflyoss.api.dfdaemon.v2.DownloadTaskResponse;
import ru.hse.dragonfly.puller.grpc.mapper.DownloadTaskRequestMapper;
import ru.hse.dragonfly.puller.grpc.mapper.DownloadTaskResponseMapper;
import ru.hse.dragonfly.puller.grpc.mapper.GrpcRetryServiceConfigMapper;

public final class DfdaemonDownloadClient implements BlobPullGateway, Closeable {
    private static final Logger LOG = LoggerFactory.getLogger(DfdaemonDownloadClient.class);
    private static final int MIN_RETRY_ATTEMPTS = 2;

    private final ManagedChannel channel;
    private final DfdaemonDownloadGrpc.DfdaemonDownloadStub asyncStub;
    private final GrpcRequestOptions requestOptions;
    private final long initialRetryBackoffMillis;
    private final long maxRetryBackoffMillis;
    private final double retryBackoffMultiplier;
    private final ScheduledExecutorService timeoutScheduler;

    public DfdaemonDownloadClient(String dfdaemonAddress) throws DragonflyPullException {
        this(dfdaemonAddress, null, null, null);
    }

    public DfdaemonDownloadClient(
            String dfdaemonAddress,
            java.time.Duration requestTimeout,
            Integer maxRetries,
            GrpcClientConfig grpcClientConfig
    ) throws DragonflyPullException {
        this.requestOptions = GrpcRequestOptionsNormalizer.normalize(requestTimeout, maxRetries);
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
        int maxAttempts = requestOptions.maxAttempts();
        if (maxAttempts >= MIN_RETRY_ATTEMPTS) {
            builder = builder.enableRetry().defaultServiceConfig(GrpcRetryServiceConfigMapper.buildDownloadTaskServiceConfig(
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
        this.timeoutScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "dfdaemon-pull-timeout");
            thread.setDaemon(true);
            return thread;
        });
        LOG.info(
                "dfdaemon client initialized: address={} requestTimeoutMs={} keepAliveTimeMs={} "
                        + "keepAliveTimeoutMs={} maxAttempts={} initialRetryBackoffMs={} maxRetryBackoffMs={} "
                        + "retryBackoffMultiplier={}",
                dfdaemonAddress,
                requestOptions.timeoutLogValue(),
                keepAliveTimeMillis,
                keepAliveTimeoutMillis,
                maxAttempts,
                initialRetryBackoffMillis,
                maxRetryBackoffMillis,
                this.retryBackoffMultiplier
        );
    }

    @Override
    public @NotNull CompletableFuture<PullResult> pull(@NotNull PullRequest request) {
        DownloadTaskRequest protoRequest = DownloadTaskRequestMapper.toProto(request);
        LOG.info(
                "starting download task: outputPath={} timeoutMs={} maxAttempts={}",
                request.outputPath(),
                requestOptions.timeoutLogValue(),
                requestOptions.maxAttempts()
        );
        AtomicBoolean finished = new AtomicBoolean(false);
        CompletableFuture<PullResult> result = new CompletableFuture<>();
        ScheduledFuture<?> timeoutFuture = null;
        if (requestOptions.isTimeoutEnabled()) {
            long requestTimeoutMillis = requestOptions.requestTimeoutMillis();
            timeoutFuture = timeoutScheduler.schedule(() -> {
                LOG.error("download task did not complete in expected time: timeoutMs={}", requestTimeoutMillis);
                DragonflyPullException timeoutError = new DragonflyPullException(
                        DragonflyPullErrorKind.TIMEOUT,
                        "download task exceeded timeout"
                );
                result.completeExceptionally(timeoutError);
            }, requestTimeoutMillis + 1000L, TimeUnit.MILLISECONDS);
        }

        ScheduledFuture<?> finalTimeoutFuture = timeoutFuture;
        result.whenComplete((ignored, throwable) -> {
            if (finalTimeoutFuture != null) {
                finalTimeoutFuture.cancel(false);
            }
        });

        try {
            DfdaemonDownloadGrpc.DfdaemonDownloadStub requestStub = asyncStub;
            if (requestOptions.isTimeoutEnabled()) {
                long requestTimeoutMillis = requestOptions.requestTimeoutMillis();
                requestStub = requestStub.withDeadlineAfter(requestTimeoutMillis, TimeUnit.MILLISECONDS);
            }
            requestStub.downloadTask(protoRequest, new StreamObserver<>() {
                    @Override
                    public void onNext(DownloadTaskResponse response) {
                        if (DownloadTaskResponseMapper.isFinished(response)) {
                            finished.set(true);
                        }
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        if (throwable instanceof StatusRuntimeException statusRuntimeException) {
                            DragonflyPullErrorKind kind = mapError(statusRuntimeException.getStatus().getCode());
                            LOG.error(
                                    "download task failed: grpcStatus={} mappedErrorKind={}",
                                    statusRuntimeException.getStatus(),
                                    kind,
                                    statusRuntimeException
                            );
                            result.completeExceptionally(
                                    new DragonflyPullException(
                                            kind,
                                            "download task failed: " + statusRuntimeException.getStatus(),
                                            statusRuntimeException
                                    )
                            );
                            return;
                        }
                        LOG.error("download task failed with unexpected internal error", throwable);
                        result.completeExceptionally(
                                new DragonflyPullException(
                                        DragonflyPullErrorKind.INTERNAL,
                                        "download task failed with internal error",
                                        throwable
                                )
                        );
                    }

                    @Override
                    public void onCompleted() {
                        if (!finished.get()) {
                            LOG.warn("download task stream completed without finished marker");
                        }
                        if (!Files.exists(request.outputPath())) {
                            LOG.error("download task finished but output file missing: outputPath={}", request.outputPath());
                            result.completeExceptionally(
                                    new DragonflyPullException(
                                            DragonflyPullErrorKind.IO,
                                            "dfdaemon completed without output file: " + request.outputPath()
                                    )
                            );
                            return;
                        }
                        LOG.info("download task completed: outputPath={}", request.outputPath());
                        result.complete(new PullResult(request.outputPath()));
                    }
                });
        } catch (RuntimeException ex) {
            LOG.error("failed to start download task", ex);
            result.completeExceptionally(
                    new DragonflyPullException(
                            DragonflyPullErrorKind.INTERNAL,
                            "failed to start download task",
                            ex
                    )
            );
        }
        return result;
    }

    @Override
    public void close() throws IOException {
        try {
            timeoutScheduler.shutdownNow();
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
}
