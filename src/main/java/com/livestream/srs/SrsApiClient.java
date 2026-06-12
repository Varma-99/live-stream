package com.livestream.srs;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.LiveStreamConfiguration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SrsApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(SrsApiClient.class);

    private final LiveStreamConfiguration configuration;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Inject
    public SrsApiClient(LiveStreamConfiguration configuration, ObjectMapper objectMapper) {
        this.configuration = configuration;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return configuration.isSrsDelivery()
                && configuration.isSrsApiEnabled()
                && configuration.getSrsApiBase() != null
                && !configuration.getSrsApiBase().isBlank();
    }

    /** Stream path (e.g. {@code live/key_high}) → cumulative recv_bytes from SRS. */
    public Map<String, Long> pathBytesReceived() {
        Map<String, Long> result = new HashMap<>();
        for (Map.Entry<String, SrsPathStats> entry : pathStats().entrySet()) {
            result.put(entry.getKey(), entry.getValue().recvBytes());
        }
        return result;
    }

    /**
     * Active publish paths with SRS-smoothed {@code kbps.recv_30s} (avoids bursty 2s delta jitter on ABR).
     */
    public Map<String, SrsPathStats> pathStats() {
        Map<String, SrsPathStats> result = new HashMap<>();
        if (!isEnabled()) {
            return result;
        }
        try {
            String base = configuration.getSrsApiBase().replaceAll("/$", "");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/api/v1/streams/"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOGGER.debug("SRS API status {}", response.statusCode());
                return result;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode streams = root.path("streams");
            if (!streams.isArray()) {
                return result;
            }
            for (JsonNode item : streams) {
                if (!item.path("publish").path("active").asBoolean(false)) {
                    continue;
                }
                long bytes = item.path("recv_bytes").asLong(0);
                long kbps30s = item.path("kbps").path("recv_30s").asLong(0);
                String url = item.path("url").asText("");
                if (url.startsWith("/")) {
                    url = url.substring(1);
                }
                SrsPathStats stats = new SrsPathStats(bytes, kbps30s);
                if (!url.isBlank()) {
                    result.put(url, stats);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("SRS API unavailable: {}", e.getMessage());
        }
        return result;
    }
}
