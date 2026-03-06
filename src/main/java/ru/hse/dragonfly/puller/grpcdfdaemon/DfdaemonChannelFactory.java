package ru.hse.dragonfly.puller.grpcdfdaemon;

import ru.hse.dragonfly.puller.error.DragonflyPullErrorKind;
import ru.hse.dragonfly.puller.error.DragonflyPullException;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DfdaemonChannelFactory {
    private static final Logger LOG = LoggerFactory.getLogger(DfdaemonChannelFactory.class);
    private static final String UNIX_SCHEME = "unix://";

    private DfdaemonChannelFactory() {
    }

    public static ManagedChannel create(String address) throws DragonflyPullException {
        return createBuilder(address).build();
    }

    public static ManagedChannelBuilder<?> createBuilder(String address) throws DragonflyPullException {
        if (address == null || address.isBlank()) {
            LOG.warn("dfdaemon channel creation failed: blank address");
            throw new DragonflyPullException(
                    DragonflyPullErrorKind.INVALID_REQUEST,
                    "dfdaemon address must not be blank"
            );
        }

        String trimmed = address.trim();
        if (trimmed.startsWith(UNIX_SCHEME)) {
            return buildUnixChannelBuilder(trimmed);
        }

        return buildTcpChannelBuilder(trimmed);
    }

    private static ManagedChannelBuilder<?> buildUnixChannelBuilder(String address) throws DragonflyPullException {
        String path = address.substring(UNIX_SCHEME.length()).trim();
        if (path.isEmpty()) {
            LOG.warn("dfdaemon channel creation failed: empty unix path, address={}", address);
            throw new DragonflyPullException(
                    DragonflyPullErrorKind.INVALID_REQUEST,
                    "invalid unix address: " + address
            );
        }

        try {
            ManagedChannelBuilder<?> channelBuilder = Grpc.newChannelBuilder(
                            UNIX_SCHEME + path,
                            InsecureChannelCredentials.create()
                    )
                    .overrideAuthority("localhost");
            LOG.info("dfdaemon channel builder created: transport=unix path={}", path);
            return channelBuilder;
        } catch (RuntimeException ex) {
            LOG.error("dfdaemon channel builder creation failed: transport=unix path={}", path, ex);
            throw new DragonflyPullException(DragonflyPullErrorKind.INTERNAL, "failed to create unix channel", ex);
        }
    }

    private static ManagedChannelBuilder<?> buildTcpChannelBuilder(String address) throws DragonflyPullException {
        int colon = address.lastIndexOf(':');
        if (colon <= 0 || colon == address.length() - 1) {
            LOG.warn("dfdaemon channel creation failed: invalid tcp address format, address={}", address);
            throw new DragonflyPullException(
                    DragonflyPullErrorKind.INVALID_REQUEST,
                    "invalid address, expected unix:///path or host:port: " + address
            );
        }

        String host = address.substring(0, colon).trim();
        String portRaw = address.substring(colon + 1).trim();
        int port;
        try {
            port = Integer.parseInt(portRaw);
        } catch (NumberFormatException ex) {
            LOG.warn("dfdaemon channel creation failed: invalid tcp port, host={} port={}", host, portRaw, ex);
            throw new DragonflyPullException(
                    DragonflyPullErrorKind.INVALID_REQUEST,
                    "invalid tcp port: " + portRaw,
                    ex
            );
        }

        try {
            ManagedChannelBuilder<?> channelBuilder = ManagedChannelBuilder.forAddress(host, port).usePlaintext();
            LOG.info("dfdaemon channel builder created: transport=tcp host={} port={} plaintext=true", host, port);
            return channelBuilder;
        } catch (RuntimeException ex) {
            LOG.error("dfdaemon channel builder creation failed: transport=tcp host={} port={}", host, port, ex);
            throw new DragonflyPullException(DragonflyPullErrorKind.INTERNAL, "failed to create tcp channel", ex);
        }
    }
}
