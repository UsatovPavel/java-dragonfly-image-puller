package ru.hse.dragonfly.puller.registry;

public sealed interface RegistryAuth permits RegistryAuth.None, RegistryAuth.Basic, RegistryAuth.Bearer {
    static RegistryAuth none() {
        return None.INSTANCE;
    }

    static RegistryAuth basic(String username, String password) {
        return new Basic(username, password);
    }

    static RegistryAuth bearer(String token) {
        return new Bearer(token);
    }

    enum None implements RegistryAuth {
        INSTANCE
    }

    record Basic(String username, String password) implements RegistryAuth {
        public Basic {
            if (username == null || username.isBlank()) {
                throw new IllegalArgumentException("basic auth username must not be blank");
            }
            if (password == null) {
                throw new IllegalArgumentException("basic auth password must not be null");
            }
        }
    }

    record Bearer(String token) implements RegistryAuth {
        public Bearer {
            if (token == null || token.isBlank()) {
                throw new IllegalArgumentException("bearer token must not be blank");
            }
        }
    }
}
