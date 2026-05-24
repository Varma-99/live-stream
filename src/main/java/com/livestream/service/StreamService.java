package com.livestream.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.LiveStreamConfiguration;
import com.livestream.api.dto.StreamResponse;
import com.livestream.dao.LiveStreamDAO;
import com.livestream.dao.UserDAO;
import com.livestream.model.LiveStream;
import com.livestream.model.StreamStatus;
import com.livestream.model.User;
import com.livestream.model.UserRole;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

@Singleton
public class StreamService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final LiveStreamConfiguration configuration;
    private final UserDAO userDAO;
    private final LiveStreamDAO liveStreamDAO;
    private final VideoService videoService;

    @Inject
    public StreamService(
            LiveStreamConfiguration configuration,
            UserDAO userDAO,
            LiveStreamDAO liveStreamDAO,
            VideoService videoService) {
        this.configuration = configuration;
        this.userDAO = userDAO;
        this.liveStreamDAO = liveStreamDAO;
        this.videoService = videoService;
    }

    public List<StreamResponse> listLiveStreams() {
        return liveStreamDAO.findByStatus(StreamStatus.LIVE).stream()
                .map(stream -> {
                    StreamResponse response = StreamResponse.from(stream);
                    videoService.applyPlaybackUrls(response, stream);
                    return response;
                })
                .toList();
    }

    public StreamResponse startStream(Long broadcasterId, String title) {
        User broadcaster = userDAO.findById(broadcasterId)
                .orElseThrow(() -> new IllegalArgumentException("Broadcaster not found: " + broadcasterId));

        if (broadcaster.getRole() != UserRole.BROADCASTER) {
            throw new IllegalArgumentException("User is not a broadcaster: " + broadcaster.getUsername());
        }

        if (liveStreamDAO.hasActiveStreamForBroadcaster(broadcasterId)) {
            throw new IllegalStateException("Broadcaster already has a live stream");
        }

        if (usesLocalCamera() && liveStreamDAO.countByStatus(StreamStatus.LIVE) > 0) {
            throw new IllegalStateException(
                    "Only one live stream at a time while using the laptop camera. Stop the current stream first.");
        }

        LiveStream stream = new LiveStream();
        stream.setBroadcaster(broadcaster);
        stream.setTitle(title.trim());
        stream.setStreamKey(generateStreamKey());
        stream.setStatus(StreamStatus.LIVE);
        stream.setViewCount(0L);
        stream.setStartedAt(Instant.now());

        LiveStream saved = liveStreamDAO.create(stream);
        try {
            videoService.startForStream(saved);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Failed to start video engine: " + e.getMessage(), e);
        }
        StreamResponse response = StreamResponse.from(saved);
        videoService.applyPlaybackUrls(response, saved);
        return response;
    }

    public StreamResponse stopStream(Long streamId) {
        LiveStream stream = liveStreamDAO.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
        if (stream.getStatus() != StreamStatus.LIVE) {
            throw new IllegalStateException("Stream is not live: " + streamId);
        }
        videoService.stopForStream(streamId);
        stream.setStatus(StreamStatus.ENDED);
        stream.setEndedAt(Instant.now());
        return StreamResponse.from(stream);
    }

    private boolean usesLocalCamera() {
        return "camera".equalsIgnoreCase(configuration.getVideoInput());
    }

    private static String generateStreamKey() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
