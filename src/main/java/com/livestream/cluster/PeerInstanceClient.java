package com.livestream.cluster;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.LiveStreamConfiguration;
import com.livestream.api.dto.StreamPostmortemResponse;
import com.livestream.api.dto.StreamQoSResponse;
import com.livestream.api.dto.StreamResponse;
import com.livestream.api.dto.ViewerQoSEventRequest;
import com.livestream.api.dto.ViewerQoSStatsRequest;
import com.livestream.config.PeerInstance;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** HTTP client for cross-instance control calls (Phase 7). */
@Singleton
public class PeerInstanceClient {

    private static final Logger LOGGER = LoggerFactory.getLogger(PeerInstanceClient.class);

    private final LiveStreamConfiguration configuration;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Inject
    public PeerInstanceClient(LiveStreamConfiguration configuration, ObjectMapper objectMapper) {
        this.configuration = configuration;
        this.objectMapper = objectMapper;
    }

    public Optional<PeerInstance> findPeer(String instanceId) {
        if (instanceId == null || instanceId.isBlank()) {
            return Optional.empty();
        }
        return configuration.getPeerInstances().stream()
                .filter(p -> instanceId.equals(p.getId()))
                .findFirst();
    }

    public StreamResponse forwardStop(String ownerInstanceId, long streamId) {
        PeerInstance peer = findPeer(ownerInstanceId)
                .orElseThrow(() -> new IllegalStateException("Unknown owner instance: " + ownerInstanceId));

        String url = normalizeHost(peer.getHost()) + "/internal/streams/" + streamId + "/stop";
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.noBody());
        if (configuration.isInternalApiEnabled()) {
            builder.header(InternalApiAuth.HEADER, configuration.getInternalApiToken());
        }

        try {
            HttpResponse<String> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code >= 200 && code < 300) {
                String body = response.body();
                if (body != null && !body.isBlank()) {
                    return objectMapper.readValue(body, StreamResponse.class);
                }
                throw new IllegalStateException("Peer stop returned empty body for stream " + streamId);
            }
            throw new IllegalStateException(
                    "Peer stop failed for stream " + streamId + " on " + ownerInstanceId + ": HTTP " + code);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.warn("Peer stop request failed: {}", e.getMessage());
            throw new IllegalStateException("Peer stop failed: " + e.getMessage(), e);
        }
    }

    public StreamQoSResponse forwardQoS(String ownerInstanceId, long streamId) {
        return forwardGet(ownerInstanceId, streamId, "/qos", StreamQoSResponse.class, "QoS");
    }

    public StreamPostmortemResponse forwardPostmortem(String ownerInstanceId, long streamId) {
        return forwardGet(ownerInstanceId, streamId, "/postmortem", StreamPostmortemResponse.class, "postmortem");
    }

    private <T> T forwardGet(
            String ownerInstanceId, long streamId, String pathSuffix, Class<T> type, String label) {
        PeerInstance peer = findPeer(ownerInstanceId)
                .orElseThrow(() -> new IllegalStateException("Unknown owner instance: " + ownerInstanceId));

        String url = normalizeHost(peer.getHost()) + "/internal/streams/" + streamId + pathSuffix;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .GET();
        if (configuration.isInternalApiEnabled()) {
            builder.header(InternalApiAuth.HEADER, configuration.getInternalApiToken());
        }

        try {
            HttpResponse<String> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code >= 200 && code < 300) {
                String body = response.body();
                if (body != null && !body.isBlank()) {
                    return objectMapper.readValue(body, type);
                }
                throw new IllegalStateException("Peer " + label + " returned empty body for stream " + streamId);
            }
            throw new IllegalStateException(
                    "Peer " + label + " failed for stream " + streamId + " on " + ownerInstanceId + ": HTTP " + code);
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.warn("Peer {} request failed: {}", label, e.getMessage());
            throw new IllegalStateException("Peer " + label + " failed: " + e.getMessage(), e);
        }
    }

    public void forwardViewerEvent(String ownerInstanceId, long streamId, ViewerQoSEventRequest request) {
        postJson(ownerInstanceId, streamId, "/qos/viewer-event", request);
    }

    public void forwardViewerStats(String ownerInstanceId, long streamId, ViewerQoSStatsRequest request) {
        postJson(ownerInstanceId, streamId, "/qos/viewer-stats", request);
    }

    private void postJson(String ownerInstanceId, long streamId, String path, Object body) {
        PeerInstance peer = findPeer(ownerInstanceId)
                .orElseThrow(() -> new IllegalStateException("Unknown owner instance: " + ownerInstanceId));

        String json;
        try {
            json = objectMapper.writeValueAsString(body);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to encode viewer QoS payload", e);
        }

        String url = normalizeHost(peer.getHost()) + "/internal/streams/" + streamId + path;
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json));
        if (configuration.isInternalApiEnabled()) {
            builder.header(InternalApiAuth.HEADER, configuration.getInternalApiToken());
        }

        try {
            HttpResponse<String> response =
                    httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code < 200 || code >= 300) {
                throw new IllegalStateException(
                        "Peer viewer QoS failed for stream " + streamId + " on " + ownerInstanceId + ": HTTP " + code);
            }
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            LOGGER.warn("Peer viewer QoS request failed: {}", e.getMessage());
            throw new IllegalStateException("Peer viewer QoS failed: " + e.getMessage(), e);
        }
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalStateException("Peer host is not configured");
        }
        return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    }
}
