package com.example.shade.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Downloads APK (or any file) from a URL with redirect following.
 * Handles tracking/affiliate redirect URLs that may contain illegal URI
 * characters
 * (like { } in redirect Location headers).
 */
@Service
public class ApkDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(ApkDownloadService.class);
    private static final int TIMEOUT_SECONDS = 60;
    private static final int MAX_REDIRECTS = 15;
    private static final long MAX_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB (Telegram limit)
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    // No automatic redirects — we follow them manually to encode illegal characters
    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    /**
     * Downloads the resource at the given URL (following redirects manually).
     * Handles redirect URLs that contain illegal URI characters like { and }.
     *
     * @param url the URL (e.g. tracking/redirect or direct APK link)
     * @return the response body as byte array, or empty on failure
     */
    public Optional<byte[]> downloadApk(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            String currentUrl = url.trim();
            for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
                logger.debug("APK download request #{}: {}", redirect, currentUrl);

                URI uri = safeCreateUri(currentUrl);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(uri)
                        .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "*/*")
                        .GET()
                        .build();

                HttpResponse<InputStream> response = httpClient.send(request,
                        HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();

                // Follow redirects (301, 302, 303, 307, 308)
                if (status >= 300 && status < 400) {
                    Optional<String> location = response.headers().firstValue("location");
                    if (location.isEmpty() || location.get().isBlank()) {
                        logger.warn("APK download: redirect {} without Location header from {}", status, currentUrl);
                        return Optional.empty();
                    }
                    String nextUrl = resolveRedirectUrl(currentUrl, location.get().trim());
                    logger.debug("APK download: {} redirect -> {}", status, nextUrl);
                    currentUrl = nextUrl;
                    // Close the redirect response body
                    response.body().close();
                    continue;
                }

                if (status < 200 || status >= 300) {
                    logger.warn("APK download failed: status {} for url {}", status, currentUrl);
                    response.body().close();
                    return Optional.empty();
                }

                // Success — read body
                try (InputStream in = response.body();
                        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    long total = 0;
                    int n;
                    while ((n = in.read(buffer)) != -1) {
                        total += n;
                        if (total > MAX_SIZE_BYTES) {
                            logger.warn("APK download too large (>{} MB) for url {}", MAX_SIZE_BYTES / (1024 * 1024),
                                    currentUrl);
                            return Optional.empty();
                        }
                        out.write(buffer, 0, n);
                    }
                    logger.info("APK download complete: {} bytes from {}", total, currentUrl);
                    return Optional.of(out.toByteArray());
                }
            }

            logger.warn("APK download: too many redirects ({}) starting from {}", MAX_REDIRECTS, url);
            return Optional.empty();
        } catch (Exception e) {
            logger.warn("APK download error for url {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Creates a URI from a raw URL string, encoding illegal characters if needed.
     * Handles URLs with { } and other characters that are invalid in strict URI
     * syntax.
     */
    private URI safeCreateUri(String rawUrl) {
        try {
            return URI.create(rawUrl);
        } catch (IllegalArgumentException e) {
            // Encode characters that are illegal in URIs but appear in some redirect URLs
            String encoded = rawUrl
                    .replace("{", "%7B")
                    .replace("}", "%7D")
                    .replace("|", "%7C")
                    .replace(" ", "%20")
                    .replace("[", "%5B")
                    .replace("]", "%5D")
                    .replace("^", "%5E");
            logger.debug("APK download: encoded illegal URI chars: {}", encoded);
            return URI.create(encoded);
        }
    }

    /**
     * Resolves a redirect Location header against the current URL.
     * Handles both absolute URLs (https://...) and relative paths (/foo/bar).
     */
    private String resolveRedirectUrl(String currentUrl, String location) {
        if (location.startsWith("http://") || location.startsWith("https://")) {
            return location;
        }
        // Relative redirect — resolve against current URL
        try {
            URI base = safeCreateUri(currentUrl);
            URI resolved = base.resolve(safeCreateUri(location));
            return resolved.toString();
        } catch (Exception e) {
            logger.warn("APK download: failed to resolve redirect '{}' against '{}': {}", location, currentUrl,
                    e.getMessage());
            return location;
        }
    }
}
