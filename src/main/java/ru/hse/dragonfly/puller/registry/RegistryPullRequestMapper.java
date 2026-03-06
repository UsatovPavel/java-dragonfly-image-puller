package ru.hse.dragonfly.puller.registry;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

import ru.hse.dragonfly.puller.blobpuller.PullRequest;

public final class RegistryPullRequestMapper {
    private static final String HTTPS_SCHEME = "https://";
    private static final String HTTP_SCHEME = "http://";

    private RegistryPullRequestMapper() {
    }

    public static PullRequest toTransportRequest(RegistryPullRequest request) {
        String digest = request.digest() == null ? "" : request.digest().trim();
        if (digest.isEmpty()) {
            throw new IllegalArgumentException("digest must not be blank");
        }
        String registryBase = normalizeRegistryBase(request.registry());
        String repository = request.repository().trim();
        String blobUrl = registryBase + "/v2/" + repository + "/blobs/" + digest;
        return new PullRequest(blobUrl, digest, request.outputPath(), buildHeaders(request));
    }

    private static String normalizeRegistryBase(String registry) {
        String trimmed = registry.trim();
        if (!trimmed.startsWith(HTTPS_SCHEME) && !trimmed.startsWith(HTTP_SCHEME)) {
            trimmed = HTTPS_SCHEME + trimmed;
        }
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    private static Map<String, String> buildHeaders(RegistryPullRequest request) {
        Map<String, String> headers = new HashMap<>();
        if (applyJwtAuthorizationHeader(request.auth(), headers)) {
            return headers;
        }
        applyBasicAuthorizationHeader(request.auth(), headers);
        return headers;
    }

    private static boolean applyJwtAuthorizationHeader(RegistryAuth auth, Map<String, String> headers) {
        if (!auth.hasJwtToken()) {
            return false;
        }
        headers.put("Authorization", "Bearer " + auth.jwtToken().trim());
        return true;
    }

    private static void applyBasicAuthorizationHeader(RegistryAuth auth, Map<String, String> headers) {
        if (!auth.hasBasicAuth()) {
            return;
        }
        String credentials = auth.basicAuthUsername() + ":" + auth.basicAuthPassword();
        String base64 = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
        headers.put("Authorization", "Basic " + base64);
    }
}
