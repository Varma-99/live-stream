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
import com.livestream.api.dto.JoinResponse;
import com.livestream.api.dto.RoomSnapshot;
import com.livestream.mediamtx.IngestHealthService;
import com.livestream.qos.QoSEventType;
import com.livestream.qos.StreamQoSService;
import com.livestream.realtime.BroadcasterControlService;
import com.livestream.realtime.LiveRoomHub;
import java.io.IOException;
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
    private final LiveRoomHub liveRoomHub;
    private final IngestHealthService ingestHealthService;
    private final BroadcasterControlService broadcasterControlService;
    private final StreamQoSService streamQoSService;

    @Inject
    public StreamService(
            LiveStreamConfiguration configuration,
            UserDAO userDAO,
            LiveStreamDAO liveStreamDAO,
            VideoService videoService,
            LiveRoomHub liveRoomHub,
            IngestHealthService ingestHealthService,
            BroadcasterControlService broadcasterControlService,
            StreamQoSService streamQoSService) {
        this.configuration = configuration;
        this.userDAO = userDAO;
        this.liveStreamDAO = liveStreamDAO;
        this.videoService = videoService;
        this.liveRoomHub = liveRoomHub;
        this.ingestHealthService = ingestHealthService;
        this.broadcasterControlService = broadcasterControlService;
        this.streamQoSService = streamQoSService;
    }

    public List<StreamResponse> listLiveStreams() {
        return liveStreamDAO.findBroadcasting().stream()
                .filter(this::visibleToViewers)
                .map(this::toStreamResponse)
                .toList();
    }

    private StreamResponse toStreamResponse(LiveStream stream) {
        StreamResponse response = StreamResponse.from(stream);
        videoService.applyPlaybackUrls(response, stream);
        response.setPublishActive(isPublishActive(stream));
        return response;
    }

    private boolean isPublishActive(LiveStream stream) {
        if (videoService.isEncoderRunning(stream.getId())) {
            return true;
        }
        return ingestHealthService.isPublishActive(stream.getId());
    }

    private boolean visibleToViewers(LiveStream stream) {
        if (!configuration.isIngestLivenessFilter()) {
            return true;
        }
        if (!videoService.isMediamtxDelivery(stream)) {
            return true;
        }
        return isPublishActive(stream);
    }

    public StreamResponse startStream(Long broadcasterId, String title, String delivery) {
        User broadcaster = userDAO.findById(broadcasterId)
                .orElseThrow(() -> new IllegalArgumentException("Broadcaster not found: " + broadcasterId));

        if (broadcaster.getRole() != UserRole.BROADCASTER) {
            throw new IllegalArgumentException("User is not a broadcaster: " + broadcaster.getUsername());
        }

        if (liveStreamDAO.hasActiveStreamForBroadcaster(broadcasterId)) {
            throw new IllegalStateException("Broadcaster already has a live stream");
        }

        if (configuration.isLocalEncoderMode() && usesLocalCamera() && liveStreamDAO.countBroadcasting() > 0) {
            throw new IllegalStateException(
                    "Only one live stream at a time while using the laptop camera. Stop the current stream first.");
        }

        boolean externalEncoder = configuration.isExternalEncoderMode();

        LiveStream stream = new LiveStream();
        stream.setBroadcaster(broadcaster);
        stream.setTitle(title.trim());
        stream.setStreamKey(generateStreamKey());
        stream.setStatus(StreamStatus.LIVE);
        stream.setViewCount(0L);
        stream.setStartedAt(Instant.now());
        stream.setDelivery(normalizeStoredDelivery(delivery));
        stream.setExternalEncoder(externalEncoder);

        LiveStream saved = liveStreamDAO.create(stream);
        if (externalEncoder) {
            if (!videoService.isMediamtxDelivery(saved)) {
                throw new IllegalStateException(
                        "External encoder mode requires WebRTC delivery (streamDelivery: srs or mediamtx)");
            }
        } else {
            try {
                videoService.startForStream(saved);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to start video engine: " + e.getMessage(), e);
            }
        }
        if (!externalEncoder) {
            broadcasterControlService.onStreamStarted(saved.getId());
        }
        streamQoSService.beginSession(saved.getId(), videoService.resolveDelivery(saved));
        return toStreamResponse(saved);
    }

    public StreamResponse startDummyStream(String title, String delivery) {
        User dummyBroadcaster = userDAO.findByUsername("dummy_streamer")
                .orElseThrow(() -> new IllegalStateException(
                        "dummy_streamer user missing — restart app in dev mode to seed users"));

        LiveStream stream = new LiveStream();
        stream.setBroadcaster(dummyBroadcaster);
        stream.setTitle(title.trim());
        stream.setStreamKey(generateStreamKey());
        stream.setStatus(StreamStatus.LIVE);
        stream.setViewCount(0L);
        stream.setStartedAt(Instant.now());
        stream.setDelivery(normalizeStoredDelivery(delivery));
        stream.setDummyStream(true);

        LiveStream saved = liveStreamDAO.create(stream);
        try {
            videoService.startDummyForStream(saved);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to start dummy encoder: " + e.getMessage(), e);
        }
        streamQoSService.beginSession(saved.getId(), videoService.resolveDelivery(saved));
        return toStreamResponse(saved);
    }

    public StreamResponse pauseStream(Long streamId) {
        LiveStream stream = liveStreamDAO.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
        if (stream.getStatus() != StreamStatus.LIVE) {
            throw new IllegalStateException("Stream is not live (cannot pause): " + streamId);
        }
        stream.setStatus(StreamStatus.PAUSED);
        return toStreamResponse(stream);
    }

    public StreamResponse resumeStream(Long streamId) {
        LiveStream stream = liveStreamDAO.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
        if (stream.getStatus() != StreamStatus.PAUSED) {
            throw new IllegalStateException("Stream is not paused: " + streamId);
        }
        stream.setStatus(StreamStatus.LIVE);
        return toStreamResponse(stream);
    }

    public StreamResponse stopStream(Long streamId) {
        LiveStream stream = liveStreamDAO.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
        if (stream.getStatus() != StreamStatus.LIVE && stream.getStatus() != StreamStatus.PAUSED) {
            throw new IllegalStateException("Stream is not active: " + streamId);
        }
        endActiveStream(streamId, stream);
        return StreamResponse.from(stream);
    }

    public void broadcasterHeartbeat(long streamId) {
        LiveStream stream = liveStreamDAO.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
        if (stream.getStatus() != StreamStatus.LIVE && stream.getStatus() != StreamStatus.PAUSED) {
            throw new IllegalStateException("Stream is not active: " + streamId);
        }
        broadcasterControlService.touch(streamId);
    }

    public void simulateIngestUnstable(long streamId, int durationSec) {
        requireDevMode();
        LiveStream stream = requireActiveStream(streamId);
        if (stream.isDummyStream() || stream.isExternalEncoder()) {
            throw new IllegalStateException("Dummy and external streams support start, pause, and stop only");
        }
        ingestHealthService.simulateUnstable(streamId, durationSec);
        streamQoSService.recordIngest(streamId, 100, true, true, true, "Demo: simulated upload problem");
    }

    public StreamResponse degradeStreamQuality(long streamId) {
        requireDevMode();
        LiveStream stream = requireActiveStream(streamId);
        if (stream.isDummyStream() || stream.isExternalEncoder()) {
            throw new IllegalStateException("Dummy and external streams support start, pause, and stop only");
        }
        try {
            videoService.degradeStream(stream);
            streamQoSService.recordDegrade(streamId, true);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to degrade stream: " + e.getMessage(), e);
        }
        return toStreamResponse(stream);
    }

    public StreamResponse restoreStreamQuality(long streamId) {
        requireDevMode();
        LiveStream stream = requireActiveStream(streamId);
        if (stream.isDummyStream() || stream.isExternalEncoder()) {
            throw new IllegalStateException("Dummy and external streams support start, pause, and stop only");
        }
        try {
            videoService.restoreStreamQuality(stream);
            streamQoSService.recordDegrade(streamId, false);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to restore stream quality: " + e.getMessage(), e);
        }
        return toStreamResponse(stream);
    }

    public void requireStreamExists(long streamId) {
        liveStreamDAO.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
    }

    public void requireActiveOrEndedStream(long streamId) {
        requireStreamExists(streamId);
    }

    private void requireDevMode() {
        if (!configuration.isDevMode()) {
            throw new IllegalStateException("This action is only available in dev mode");
        }
    }

    private LiveStream requireActiveStream(long streamId) {
        LiveStream stream = liveStreamDAO.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
        if (stream.getStatus() != StreamStatus.LIVE && stream.getStatus() != StreamStatus.PAUSED) {
            throw new IllegalStateException("Stream is not active: " + streamId);
        }
        return stream;
    }

    private void endActiveStream(long streamId, LiveStream stream) {
        videoService.stopAnyEncoderForStream(streamId);
        ingestHealthService.clear(streamId);
        streamQoSService.endSession(streamId, false);
        broadcasterControlService.onStreamStopped(streamId);
        liveRoomHub.closeRoom(streamId);
        stream.setStatus(StreamStatus.ENDED);
        stream.setEndedAt(Instant.now());
    }

    public void dropCoupon(Long streamId, String code, int percentOff, int durationSec) {
        liveStreamDAO.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
        liveRoomHub.dropCoupon(streamId, code.toUpperCase(), percentOff, durationSec);
    }

    public JoinResponse joinRoom(Long streamId) {
        liveStreamDAO.findById(streamId)
                .orElseThrow(() -> new IllegalArgumentException("Stream not found: " + streamId));
        String presenceId = liveRoomHub.join(streamId);
        streamQoSService.recordViewerEvent(streamId, presenceId, QoSEventType.VIEWER_JOIN_OK, "Viewer joined", null);
        return new JoinResponse(presenceId, enrichRoom(streamId, liveRoomHub.snapshot(streamId)));
    }

    public RoomSnapshot heartbeat(Long streamId, String presenceId) {
        liveRoomHub.heartbeat(streamId, presenceId);
        return enrichRoom(streamId, liveRoomHub.snapshot(streamId));
    }

    public void leaveRoom(Long streamId, String presenceId) {
        liveRoomHub.leave(streamId, presenceId);
    }

    public RoomSnapshot roomSnapshot(Long streamId) {
        return enrichRoom(streamId, liveRoomHub.snapshot(streamId));
    }

    public RoomSnapshot recordLike(Long streamId, String presenceId) {
        liveRoomHub.heartbeat(streamId, presenceId);
        liveRoomHub.recordLike(streamId);
        return enrichRoom(streamId, liveRoomHub.snapshot(streamId));
    }

    private RoomSnapshot enrichRoom(long streamId, RoomSnapshot base) {
        var ingest = ingestHealthService.snapshot(streamId);
        boolean showIngest = ingest.monitoring() || ingest.unstable();
        boolean encodeDegraded = videoService.isDegraded(streamId);
        if (!showIngest && !encodeDegraded) {
            return base;
        }
        String warning = ingest.unstable()
                ? (ingest.reason() != null ? ingest.reason() : "Network unstable — check your upload")
                : null;
        return new RoomSnapshot(
                base.getViewers(),
                base.getLikes(),
                base.getCoupon(),
                base.getStreamStatus(),
                ingest.unstable(),
                ingest.ingestKbps(),
                warning,
                encodeDegraded);
    }

    private boolean usesLocalCamera() {
        return "camera".equalsIgnoreCase(configuration.getVideoInput());
    }

    private static String generateStreamKey() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** {@code auto} or blank → null (global default); otherwise {@code hls} or {@code webrtc}. */
    private static String normalizeStoredDelivery(String delivery) {
        if (delivery == null || delivery.isBlank() || "auto".equalsIgnoreCase(delivery)) {
            return null;
        }
        return delivery.trim().toLowerCase();
    }
}
