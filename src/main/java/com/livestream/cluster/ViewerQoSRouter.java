package com.livestream.cluster;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.LiveStreamConfiguration;
import com.livestream.api.dto.ViewerQoSEventRequest;
import com.livestream.api.dto.ViewerQoSStatsRequest;
import com.livestream.qos.QoSEventType;
import com.livestream.qos.StreamQoSService;
import com.livestream.realtime.BroadcasterControlService;
import com.livestream.redis.RedisService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Routes viewer QoS beacons to the JVM that owns the live stream session. */
@Singleton
public class ViewerQoSRouter {

    private static final Logger LOGGER = LoggerFactory.getLogger(ViewerQoSRouter.class);

    private final LiveStreamConfiguration configuration;
    private final RedisService redisService;
    private final BroadcasterControlService broadcasterControlService;
    private final PeerInstanceClient peerInstanceClient;
    private final StreamQoSService streamQoSService;

    @Inject
    public ViewerQoSRouter(
            LiveStreamConfiguration configuration,
            RedisService redisService,
            BroadcasterControlService broadcasterControlService,
            PeerInstanceClient peerInstanceClient,
            StreamQoSService streamQoSService) {
        this.configuration = configuration;
        this.redisService = redisService;
        this.broadcasterControlService = broadcasterControlService;
        this.peerInstanceClient = peerInstanceClient;
        this.streamQoSService = streamQoSService;
    }

    public void recordViewerEvent(
            long streamId, String presenceId, QoSEventType type, String message, String detail) {
        if (forwardViewerEvent(streamId, presenceId, type.name(), message, detail)) {
            return;
        }
        if (!isLocalOwner(streamId)) {
            LOGGER.warn(
                    "Viewer event for stream {} stored locally after owner forward failed; owner QoS may be incomplete",
                    streamId);
        }
        streamQoSService.ensureSession(streamId);
        streamQoSService.recordViewerEvent(streamId, presenceId, type, message, detail);
    }

    public void recordViewerEvent(long streamId, ViewerQoSEventRequest request, QoSEventType type) {
        String message = request.getMessage() != null ? request.getMessage() : type.name();
        recordViewerEvent(streamId, request.getPresenceId(), type, message, request.getDetail());
    }

    public void recordViewerStats(long streamId, ViewerQoSStatsRequest request) {
        if (forwardViewerStats(streamId, request)) {
            return;
        }
        if (!isLocalOwner(streamId)) {
            LOGGER.warn(
                    "Viewer stats for stream {} stored locally after owner forward failed; owner QoS may be incomplete",
                    streamId);
        }
        streamQoSService.ensureSession(streamId);
        streamQoSService.recordWebRtcStats(
                streamId,
                request.getPresenceId(),
                request.getQualityLabel(),
                request.getPacketLossPct() != null ? request.getPacketLossPct() : 0,
                request.getRttMs() != null ? request.getRttMs() : 0,
                request.getJitterMs() != null ? request.getJitterMs() : 0,
                request.getDownloadKbps() != null ? request.getDownloadKbps() : 0,
                request.getAvgDelayMs() != null ? request.getAvgDelayMs() : 0);
    }

    private boolean forwardViewerEvent(
            long streamId, String presenceId, String eventType, String message, String detail) {
        Optional<String> owner = remoteOwner(streamId);
        if (owner.isEmpty()) {
            return false;
        }
        try {
            ViewerQoSEventRequest request = new ViewerQoSEventRequest();
            request.setPresenceId(presenceId);
            request.setEventType(eventType);
            request.setMessage(message);
            request.setDetail(detail);
            peerInstanceClient.forwardViewerEvent(owner.get(), streamId, request);
            return true;
        } catch (RuntimeException e) {
            LOGGER.warn("Viewer event forward failed for stream {}: {}", streamId, e.getMessage());
            return false;
        }
    }

    private boolean forwardViewerStats(long streamId, ViewerQoSStatsRequest request) {
        Optional<String> owner = remoteOwner(streamId);
        if (owner.isEmpty()) {
            return false;
        }
        try {
            peerInstanceClient.forwardViewerStats(owner.get(), streamId, request);
            return true;
        } catch (RuntimeException e) {
            LOGGER.warn("Viewer stats forward failed for stream {}: {}", streamId, e.getMessage());
            return false;
        }
    }

    private Optional<String> remoteOwner(long streamId) {
        if (isLocalOwner(streamId)) {
            return Optional.empty();
        }
        if (redisService.isEnabled()) {
            return redisService.getStreamOwner(streamId).filter(id -> !id.isBlank());
        }
        return Optional.empty();
    }

    private boolean isLocalOwner(long streamId) {
        String self = configuration.getInstanceId();
        if (redisService.isEnabled()) {
            return redisService.getStreamOwner(streamId).map(self::equals).orElseGet(
                    () -> broadcasterControlService.isLocallyOwned(streamId));
        }
        return broadcasterControlService.isLocallyOwned(streamId);
    }
}
