package com.livestream.mediamtx;

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
public class MediamtxApiClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(MediamtxApiClient.class);

    private final LiveStreamConfiguration configuration;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    @Inject
    public MediamtxApiClient(LiveStreamConfiguration configuration, ObjectMapper objectMapper) {
        this.configuration = configuration;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return configuration.isMediamtxDelivery() && configuration.isMediamtxApiEnabled();
    }

    /** Path name → bytes received (uses inboundBytes or bytesReceived). */
    public Map<String, Long> pathBytesReceived() {
        Map<String, Long> result = new HashMap<>();
        if (!isEnabled()) {
            return result;
        }
        try {
            String base = configuration.getMediamtxApiBase().replaceAll("/$", "");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/v3/paths/list"))
                    .timeout(Duration.ofSeconds(3))
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                LOGGER.debug("MediaMTX API status {}", response.statusCode());
                return result;
            }
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode items = root.path("items");
            if (!items.isArray()) {
                return result;
            }
            for (JsonNode item : items) {
                String name = item.path("name").asText(null);
                if (name == null || name.isBlank()) {
                    continue;
                }
                long bytes = item.path("inboundBytes").asLong(0);
                if (bytes == 0) {
                    bytes = item.path("bytesReceived").asLong(0);
                }
                result.put(name, bytes);
            }
        } catch (Exception e) {
            LOGGER.debug("MediaMTX API unavailable: {}", e.getMessage());
        }
        return result;
    }
}
