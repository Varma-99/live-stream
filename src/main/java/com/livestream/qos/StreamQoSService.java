package com.livestream.qos;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.LiveStreamConfiguration;
import com.livestream.api.dto.EncoderQoSDto;
import com.livestream.api.dto.IngestQoSDto;
import com.livestream.api.dto.OpsQoSDto;
import com.livestream.api.dto.QoSEventDto;
import com.livestream.api.dto.StreamPostmortemResponse;
import com.livestream.api.dto.StreamQoSResponse;
import com.livestream.api.dto.ViewerDeliveryQoSDto;
import com.livestream.api.dto.ViewerQoEAggregateDto;
import com.livestream.dao.LiveStreamDAO;
import com.livestream.dao.QoSSessionDAO;
import com.livestream.model.LiveStream;
import com.livestream.realtime.LiveRoomHub;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StreamQoSService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamQoSService.class);
    private static final long DEFAULT_MIN_INGEST_KBPS = 300;

    private final LiveStreamConfiguration configuration;
    private final LiveRoomHub liveRoomHub;
    private final LiveStreamDAO liveStreamDAO;
    private final QoSSessionDAO qosSessionDAO;
    private final ConcurrentHashMap<Long, StreamQoSSession> sessions = new ConcurrentHashMap<>();

    @Inject
    public StreamQoSService(
            LiveStreamConfiguration configuration,
            LiveRoomHub liveRoomHub,
            LiveStreamDAO liveStreamDAO,
            QoSSessionDAO qosSessionDAO) {
        this.configuration = configuration;
        this.liveRoomHub = liveRoomHub;
        this.liveStreamDAO = liveStreamDAO;
        this.qosSessionDAO = qosSessionDAO;
    }

    public void beginSession(long streamId, String deliveryMode) {
        StreamQoSSession session = new StreamQoSSession(streamId);
        session.setEncodeTargetFps(30);
        session.setDeliveryMode(deliveryMode != null ? deliveryMode : defaultDeliveryMode());
        session.addEvent(QoSEvent.of(QoSStage.OPS, QoSEventType.STREAM_STARTED, "Stream session started"));
        sessions.put(streamId, session);
    }

    /** Viewer beacons may arrive before broadcaster hooks; avoid dropping metrics. */
    public void ensureSession(long streamId) {
        sessions.computeIfAbsent(streamId, id -> {
            StreamQoSSession session = new StreamQoSSession(id);
            session.setEncodeTargetFps(30);
            session.setDeliveryMode(defaultDeliveryMode());
            return session;
        });
    }

    public void endSession(long streamId, boolean zombie) {
        StreamQoSSession session = sessions.get(streamId);
        if (session == null) {
            return;
        }
        if (zombie) {
            session.markZombieStop();
        }
        session.finalizeSession();
        persistSession(streamId, session);
        removeSession(streamId);
    }

    private void persistSession(long streamId, StreamQoSSession session) {
        try {
            LiveStream stream = liveStreamDAO.findById(streamId).orElse(null);
            if (stream == null) {
                return;
            }
            long minKbps = minIngestKbps();
            int overallScore = session.overallHealthScore(minKbps);
            String rootCause = inferRootCause(session);
            long ended = session.endedAtMs() != null ? session.endedAtMs() : System.currentTimeMillis();
            List<QoSEventDto> events = session.allEvents().stream().map(StreamQoSService::toDto).toList();
            qosSessionDAO.save(
                    stream,
                    session.startedAtMs(),
                    session.endedAtMs(),
                    session.zombieStop(),
                    overallScore,
                    rootCause,
                    ended - session.startedAtMs(),
                    session.deliveryMode(),
                    events,
                    session.viewerSessionDtos());
        } catch (Exception e) {
            LOGGER.warn("Failed to persist QoS session for stream {}: {}", streamId, e.getMessage());
        }
    }

    public void removeSession(long streamId) {
        sessions.remove(streamId);
    }

    public StreamQoSSession session(long streamId) {
        return sessions.get(streamId);
    }

    public void recordIngest(long streamId, long kbps, boolean monitoring, boolean stalled, boolean unstable, String reason) {
        StreamQoSSession session = sessions.get(streamId);
        if (session == null) {
            return;
        }
        if (!monitoring) {
            session.setIngestUnstable(true, QoSEventType.INGEST_NO_PATH, reason != null ? reason : "No ingest on MediaMTX");
            return;
        }
        session.recordIngestSample(kbps, stalled, unstable);
        if (stalled) {
            session.addEvent(QoSEvent.of(QoSStage.INGEST, QoSEventType.INGEST_STALL, "Upload stalled"));
        }
        if (unstable) {
            session.setIngestUnstable(
                    true,
                    stalled ? QoSEventType.INGEST_STALL : QoSEventType.INGEST_UNSTABLE,
                    reason != null ? reason : "Upload bitrate low");
        } else {
            session.setIngestUnstable(false, QoSEventType.INGEST_RECOVERED, null);
        }
    }

    public void recordFfmpegLine(long streamId, String line) {
        StreamQoSSession session = sessions.get(streamId);
        if (session == null) {
            return;
        }
        FfmpegLogParser.parseLine(line).ifPresent(parsed -> {
            session.recordEncoder(parsed);
            if ("hls".equals(session.deliveryMode())
                    && parsed.bitrateKbps() != null
                    && parsed.bitrateKbps() > 0) {
                long kbps = Math.round(parsed.bitrateKbps());
                session.recordIngestSample(kbps, false, kbps < minIngestKbps());
            }
        });
    }

    public void recordEncodeStarted(long streamId) {
        StreamQoSSession s = sessions.get(streamId);
        if (s != null) {
            s.recordEncodeStarted();
        }
    }

    public void recordEncodeRestart(long streamId) {
        StreamQoSSession s = sessions.get(streamId);
        if (s != null) {
            s.recordEncodeRestart();
        }
    }

    public void recordDegrade(long streamId, boolean degraded) {
        StreamQoSSession s = sessions.get(streamId);
        if (s != null) {
            s.recordDegrade(degraded);
        }
    }

    public void recordViewerEvent(long streamId, String presenceId, QoSEventType type, String message, String detail) {
        StreamQoSSession s = sessions.get(streamId);
        if (s != null) {
            s.recordViewerEvent(presenceId, type, message, detail);
        }
    }

    public void recordWebRtcStats(
            long streamId,
            String presenceId,
            String qualityLabel,
            double packetLossPct,
            long rttMs,
            double jitterMs,
            long downloadKbps) {
        StreamQoSSession s = sessions.get(streamId);
        if (s != null) {
            s.recordWebRtcStats(presenceId, qualityLabel, packetLossPct, rttMs, jitterMs, downloadKbps);
        }
    }

    public StreamQoSResponse snapshot(long streamId) {
        StreamQoSSession s = sessions.get(streamId);
        if (s == null) {
            return StreamQoSResponse.empty(streamId);
        }
        return buildResponse(s, minIngestKbps(), false);
    }

    public StreamPostmortemResponse postmortem(long streamId) {
        StreamQoSSession s = sessions.get(streamId);
        if (s == null) {
            return StreamPostmortemResponse.empty(streamId);
        }
        long minKbps = minIngestKbps();
        StreamQoSResponse qos = buildResponse(s, minKbps, true);
        long durationMs = (s.endedAtMs() != null ? s.endedAtMs() : System.currentTimeMillis()) - s.startedAtMs();
        String rootCause = inferRootCause(s);
        return new StreamPostmortemResponse(
                streamId,
                durationMs,
                s.zombieStop(),
                qos,
                rootCause,
                s.allEvents().stream().map(StreamQoSService::toDto).collect(Collectors.toList()));
    }

    public List<StreamPostmortemResponse> historyForStream(long streamId) {
        return qosSessionDAO.findByStreamId(streamId);
    }

    public List<StreamPostmortemResponse> recentHistory(int limit) {
        return qosSessionDAO.findRecent(limit);
    }

    public OpsQoSDto globalOps() {
        int activeSessions = sessions.size();
        int viewers = liveRoomHub.totalViewers();
        return new OpsQoSDto(activeSessions, viewers, sessions.keySet().stream().toList());
    }

    private StreamQoSResponse buildResponse(StreamQoSSession s, long minKbps, boolean includeAllEvents) {
        IngestQoSDto ingest = new IngestQoSDto(
                s.ingestKbpsAvg(),
                s.ingestKbpsP95(),
                s.ingestKbpsStdDev(),
                s.ingestStallCount(),
                s.ingestStallTotalMs(),
                s.ingestRecoveryCount(),
                s.ingestRecoveryTotalMs(),
                s.ingestHealthScore(minKbps),
                s.ingestUnstable(),
                s.currentIngestKbps());

        EncoderQoSDto encode = new EncoderQoSDto(
                s.encodeFpsActual(),
                s.encodeTargetFps(),
                s.encodeSpeedRatio(),
                s.encodeDroppedFrames(),
                s.encodeDuplicatedFrames(),
                s.encodeRestartCount(),
                s.encodeErrorCount(),
                s.encodeHealthScore());

        ViewerDeliveryQoSDto delivery = new ViewerDeliveryQoSDto(
                s.avgStartupMs(),
                s.deliveryStallCount(),
                s.deliveryReconnectCount(),
                s.deliveryAbrDownCount(),
                s.deliveryAbrUpCount(),
                s.deliveryMode(),
                s.avgPacketLossPct(),
                s.avgRttMs(),
                s.avgJitterMs());
        delivery.setDeliveryHealthScore(s.deliveryHealthScore());

        int joins = s.viewerJoinOk() + s.viewerJoinFail();
        double joinSuccess = joins == 0 ? 100.0 : (100.0 * s.viewerJoinOk() / joins);
        ViewerQoEAggregateDto viewer = new ViewerQoEAggregateDto(
                joinSuccess,
                s.avgStartupMs(),
                s.viewerWatchMsTotal(),
                s.viewerStallsPerHour(),
                s.deliveryAbrDownCount() + s.deliveryAbrUpCount(),
                s.viewerFatalCount(),
                s.viewerHealthScore());

        OpsQoSDto ops = new OpsQoSDto(
                1,
                liveRoomHub.viewersForStream(s.streamId()),
                List.of(s.streamId()));
        ops.setMttdMs(s.mttdMs());
        ops.setMttrMs(s.mttrMs());
        ops.setDeliveryMttdMs(s.deliveryMttdMs());
        ops.setDeliveryMttrMs(s.deliveryMttrMs());
        ops.setZombieStop(s.zombieStop());

        List<QoSEventDto> events = (includeAllEvents ? s.allEvents() : s.recentEvents(20))
                .stream()
                .map(StreamQoSService::toDto)
                .collect(Collectors.toList());

        return new StreamQoSResponse(
                s.streamId(),
                s.overallHealthScore(minKbps),
                ingest,
                encode,
                delivery,
                viewer,
                ops,
                events,
                s.viewerSessionDtos());
    }

    static String inferRootCause(StreamQoSSession s) {
        if (s.ingestUnstable() || s.ingestStallCount() > 2) {
            return "ingest";
        }
        if (s.encodeRestartCount() > 1 || s.encodeErrorCount() > 0 || s.encodeSpeedRatio() < 0.9) {
            return "encode";
        }
        if (s.deliveryStallCount() > 2 || s.viewerFatalCount() > 0) {
            return "delivery";
        }
        if (s.viewerJoinFail() > 0) {
            return "viewer";
        }
        return "none";
    }

    private static QoSEventDto toDto(QoSEvent e) {
        return new QoSEventDto(e.atEpochMs(), e.stage().name(), e.type().name(), e.message(), e.detail());
    }

    private long minIngestKbps() {
        return DEFAULT_MIN_INGEST_KBPS;
    }

    private String defaultDeliveryMode() {
        return configuration.isMediamtxDelivery() ? "webrtc" : "hls";
    }
}
