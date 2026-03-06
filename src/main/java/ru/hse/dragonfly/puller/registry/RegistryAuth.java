package ru.hse.dragonfly.puller.registry;

public record RegistryAuth(
        String basicAuthUsername,
        String basicAuthPassword,
        String jwtToken
) {
    public static RegistryAuth none() {
        return new RegistryAuth(null, null, null);
    }

    public boolean hasJwtToken() {
        return jwtToken != null && !jwtToken.isBlank();
    }

    public boolean hasBasicAuth() {
        return basicAuthUsername != null && !basicAuthUsername.isBlank() && basicAuthPassword != null;
    }
}
