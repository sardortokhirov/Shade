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
 * Used when apk_url is a redirect/tracking link; we download on our server then send to Telegram.
 */
@Service
public class ApkDownloadService {

    private static final Logger logger = LoggerFactory.getLogger(ApkDownloadService.class);
    private static final int TIMEOUT_SECONDS = 30;
    private static final long MAX_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB (Telegram limit)
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; ShadeApkBot/1.0)";

    private final HttpClient httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Downloads the resource at the given URL (following redirects).
     *
     * @param url the URL (e.g. tracking/redirect or direct APK link)
     * @return the response body as byte array, or empty on failure (timeout, non-2xx, too large)
     */
    public Optional<byte[]> downloadApk(String url) {
        if (url == null || url.isBlank()) {
            return Optional.empty();
        }
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url.trim()))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .header("User-Agent", USER_AGENT)
                    .GET()
                    .build();

            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                logger.warn("APK download failed: status {} for url {}", response.statusCode(), url);
                return Optional.empty();
            }

            try (InputStream in = response.body();
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int n;
                while ((n = in.read(buffer)) != -1) {
                    total += n;
                    if (total > MAX_SIZE_BYTES) {
                        logger.warn("APK download too large (>{} MB) for url {}", MAX_SIZE_BYTES / (1024 * 1024), url);
                        return Optional.empty();
                    }
                    out.write(buffer, 0, n);
                }
                return Optional.of(out.toByteArray());
            }
        } catch (Exception e) {
            logger.warn("APK download error for url {}: {}", url, e.getMessage());
            return Optional.empty();
        }
    }
}
