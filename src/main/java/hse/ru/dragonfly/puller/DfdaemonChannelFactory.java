package hse.ru.dragonfly.puller;

import hse.ru.dragonfly.puller.error.DragonflyPullException;
import hse.ru.dragonfly.puller.error.ErrorKind;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;

public final class DfdaemonChannelFactory {
    private DfdaemonChannelFactory() {
    }

    public static ManagedChannel create(String address) throws DragonflyPullException {
        if (address == null || address.isBlank()) {
            throw new DragonflyPullException(ErrorKind.INVALID_REQUEST, "dfdaemon address must not be blank");
        }

        String trimmed = address.trim();
        if (trimmed.startsWith("unix://")) {
            return buildUnixChannel(trimmed);
        }

        return buildTcpChannel(trimmed);
    }

    private static ManagedChannel buildUnixChannel(String address) throws DragonflyPullException {
        String path = address.substring("unix://".length()).trim();
        if (path.isEmpty()) {
            throw new DragonflyPullException(ErrorKind.INVALID_REQUEST, "invalid unix address: " + address);
        }

        try {
            return Grpc.newChannelBuilder("unix://" + path, InsecureChannelCredentials.create())
                    .overrideAuthority("localhost")
                    .build();
        } catch (RuntimeException ex) {
            throw new DragonflyPullException(ErrorKind.INTERNAL, "failed to create unix channel", ex);
        }
    }

    private static ManagedChannel buildTcpChannel(String address) throws DragonflyPullException {
        int colon = address.lastIndexOf(':');
        if (colon <= 0 || colon == address.length() - 1) {
            throw new DragonflyPullException(
                    ErrorKind.INVALID_REQUEST,
                    "invalid address, expected unix:///path or host:port: " + address
            );
        }

        String host = address.substring(0, colon).trim();
        String portRaw = address.substring(colon + 1).trim();
        int port;
        try {
            port = Integer.parseInt(portRaw);
        } catch (NumberFormatException ex) {
            throw new DragonflyPullException(ErrorKind.INVALID_REQUEST, "invalid tcp port: " + portRaw, ex);
        }

        try {
            return NettyChannelBuilder.forAddress(host, port)
                    .usePlaintext()
                    .build();
        } catch (RuntimeException ex) {
            throw new DragonflyPullException(ErrorKind.INTERNAL, "failed to create tcp channel", ex);
        }
    }
}
