package com.livestream.qos;

import com.livestream.api.dto.ViewerSessionQoSDto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Per-stream QoS state (in-memory). */
final class StreamQoSSession {

    private static final int MAX_EVENTS = 200;
    private static final int KBPS_WINDOW = 60;

    private final long streamId;
    private final long startedAtMs;
    private final RollingStats ingestKbpsWindow = new RollingStats(KBPS_WINDOW);

    private final List<QoSEvent> events = Collections.synchronizedList(new ArrayList<>());
    private final ConcurrentHashMap<String, ViewerSessionState> viewers = new ConcurrentHashMap<>();

    private final AtomicBoolean ingestUnstable = new AtomicBoolean(false);
    private final AtomicInteger ingestStallCount = new AtomicInteger(0);
    private final AtomicLong ingestStallTotalMs = new AtomicLong(0);
    private final AtomicInteger ingestRecoveryCount = new AtomicInteger(0);
    private final AtomicLong ingestRecoveryTotalMs = new AtomicLong(0);
    private volatile Long ingestIssueStartedMs;
    private volatile long lastIngestSampleMs;
    private volatile long currentIngestKbps;

    private final AtomicInteger encodeRestartCount = new AtomicInteger(0);
    private final AtomicInteger encodeErrorCount = new AtomicInteger(0);
    private final AtomicLong encodeDroppedFrames = new AtomicLong(0);
    private final AtomicLong encodeDuplicatedFrames = new AtomicLong(0);
    private volatile double encodeFpsActual;
    private volatile double encodeSpeedRatio = 1.0;
    private volatile int encodeTargetFps = 30;
    private volatile boolean encodeSlow;

    private final AtomicInteger deliveryReconnectCount = new AtomicInteger(0);
    private final AtomicInteger deliveryAbrDownCount = new AtomicInteger(0);
    private final AtomicInteger deliveryAbrUpCount = new AtomicInteger(0);
    private final AtomicInteger deliveryStallCount = new AtomicInteger(0);
    private final AtomicLong deliveryStartupMsTotal = new AtomicLong(0);
    private final AtomicInteger deliveryStartupCount = new AtomicInteger(0);

    private volatile Long deliveryFirstIssueAtMs;
    private volatile Long activeDeliveryOutageStartedMs;
    private final AtomicInteger deliveryRecoveryCount = new AtomicInteger(0);
    private final AtomicLong deliveryRecoveryTotalMs = new AtomicLong(0);

    private final AtomicInteger webrtcStatsSampleCount = new AtomicInteger(0);
    private final AtomicLong webrtcLossMilliSum = new AtomicLong(0);
    private final AtomicLong webrtcRttSum = new AtomicLong(0);
    private final AtomicLong webrtcJitterMilliSum = new AtomicLong(0);

    private final AtomicInteger viewerJoinOk = new AtomicInteger(0);
    private final AtomicInteger viewerJoinFail = new AtomicInteger(0);
    private final AtomicInteger viewerFatalCount = new AtomicInteger(0);
    private final AtomicLong viewerWatchMsTotal = new AtomicLong(0);

    private volatile boolean zombieStop;
    private volatile boolean finalized;
    private volatile Long endedAtMs;

    StreamQoSSession(long streamId) {
        this.streamId = streamId;
        this.startedAtMs = System.currentTimeMillis();
    }

    long streamId() {
        return streamId;
    }

    long startedAtMs() {
        return startedAtMs;
    }

    void addEvent(QoSEvent event) {
        synchronized (events) {
            events.add(event);
            while (events.size() > MAX_EVENTS) {
                events.remove(0);
            }
        }
    }

    List<QoSEvent> recentEvents(int limit) {
        synchronized (events) {
            int size = events.size();
            if (size <= limit) {
                return List.copyOf(events);
            }
            return List.copyOf(events.subList(size - limit, size));
        }
    }

    List<QoSEvent> allEvents() {
        synchronized (events) {
            return List.copyOf(events);
        }
    }

    void recordIngestSample(long kbps, boolean stalled, boolean unstable) {
        long now = System.currentTimeMillis();
        currentIngestKbps = kbps;
        lastIngestSampleMs = now;
        if (kbps > 0) {
            ingestKbpsWindow.add(kbps);
        }
        if (stalled) {
            ingestStallCount.incrementAndGet();
            if (ingestIssueStartedMs == null) {
                ingestIssueStartedMs = now;
            }
        }
    }

    void setIngestUnstable(boolean unstable, QoSEventType transitionEvent, String message) {
        boolean was = ingestUnstable.getAndSet(unstable);
        if (unstable && !was) {
            ingestIssueStartedMs = System.currentTimeMillis();
            addEvent(QoSEvent.of(QoSStage.INGEST, transitionEvent, message));
        } else if (!unstable && was) {
            long started = ingestIssueStartedMs != null ? ingestIssueStartedMs : System.currentTimeMillis();
            long duration = System.currentTimeMillis() - started;
            ingestRecoveryCount.incrementAndGet();
            ingestRecoveryTotalMs.addAndGet(duration);
            ingestIssueStartedMs = null;
            addEvent(QoSEvent.of(QoSStage.INGEST, QoSEventType.INGEST_RECOVERED, "Upload recovered", duration + "ms"));
        }
    }

    void recordEncoder(FfmpegLogParser.EncoderParseResult parsed) {
        if (parsed.isError()) {
            encodeErrorCount.incrementAndGet();
            addEvent(QoSEvent.of(QoSStage.ENCODE, QoSEventType.ENCODE_ERROR, parsed.errorMessage()));
            return;
        }
        if (parsed.fps() != null) {
            encodeFpsActual = parsed.fps();
        }
        if (parsed.speedRatio() != null) {
            encodeSpeedRatio = parsed.speedRatio();
            boolean slow = parsed.speedRatio() < 0.95;
            if (slow && !encodeSlow) {
                encodeSlow = true;
                addEvent(QoSEvent.of(
                        QoSStage.ENCODE,
                        QoSEventType.ENCODE_SLOW,
                        "Encoder behind real-time",
                        String.format("%.2fx", parsed.speedRatio())));
            } else if (!slow) {
                encodeSlow = false;
            }
        }
        encodeDroppedFrames.set(parsed.droppedFrames());
        encodeDuplicatedFrames.set(parsed.duplicatedFrames());
    }

    void recordEncodeRestart() {
        encodeRestartCount.incrementAndGet();
        addEvent(QoSEvent.of(QoSStage.ENCODE, QoSEventType.ENCODE_RESTART, "FFmpeg restarted"));
    }

    void recordEncodeStarted() {
        addEvent(QoSEvent.of(QoSStage.ENCODE, QoSEventType.ENCODE_STARTED, "FFmpeg started"));
    }

    void recordDegrade(boolean degraded) {
        addEvent(QoSEvent.of(
                QoSStage.ENCODE,
                degraded ? QoSEventType.ENCODE_DEGRADED : QoSEventType.ENCODE_RESTORED,
                degraded ? "Manual degrade" : "Quality restored"));
    }

    ViewerSessionState viewerSession(String presenceId) {
        return viewers.computeIfAbsent(presenceId, ViewerSessionState::new);
    }

    void recordViewerEvent(String presenceId, QoSEventType type, String message, String detail) {
        ViewerSessionState vs = presenceId != null ? viewerSession(presenceId) : null;
        switch (type) {
            case VIEWER_JOIN_OK -> viewerJoinOk.incrementAndGet();
            case VIEWER_JOIN_FAIL -> viewerJoinFail.incrementAndGet();
            case VIEWER_FATAL -> {
                viewerFatalCount.incrementAndGet();
                if (vs != null) {
                    vs.fatalCount.incrementAndGet();
                }
            }
            case VIEWER_TTFF -> {
                deliveryStartupCount.incrementAndGet();
                if (detail != null) {
                    try {
                        long ms = Long.parseLong(detail);
                        deliveryStartupMsTotal.addAndGet(ms);
                        if (vs != null) {
                            vs.ttffMs = ms;
                        }
                    } catch (NumberFormatException ignored) {
                        // ignore
                    }
                }
            }
            case DELIVERY_STALL, VIEWER_STALL -> {
                deliveryStallCount.incrementAndGet();
                markDeliveryIssueStart();
            }
            case DELIVERY_ABR_STEP_DOWN -> deliveryAbrDownCount.incrementAndGet();
            case DELIVERY_ABR_STEP_UP -> deliveryAbrUpCount.incrementAndGet();
            case DELIVERY_RECONNECT -> {
                deliveryReconnectCount.incrementAndGet();
                markDeliveryIssueStart();
            }
            case DELIVERY_RECOVERED -> {
                long recoveryMs = parseRecoveryMs(detail);
                if (recoveryMs <= 0 && activeDeliveryOutageStartedMs != null) {
                    recoveryMs = System.currentTimeMillis() - activeDeliveryOutageStartedMs;
                }
                if (recoveryMs > 0) {
                    deliveryRecoveryTotalMs.addAndGet(recoveryMs);
                    deliveryRecoveryCount.incrementAndGet();
                }
                activeDeliveryOutageStartedMs = null;
            }
            default -> {
                // no-op
            }
        }
        if (vs != null) {
            vs.apply(type, detail);
        }
        addEvent(QoSEvent.of(QoSStage.VIEWER, type, message, detail));
    }

    void recordWebRtcStats(
            String presenceId,
            String qualityLabel,
            double packetLossPct,
            long rttMs,
            double jitterMs,
            long downloadKbps) {
        if (presenceId == null || presenceId.isBlank()) {
            return;
        }
        ViewerSessionState vs = viewerSession(presenceId);
        vs.updateStats(qualityLabel, packetLossPct, rttMs, jitterMs, downloadKbps);
        webrtcStatsSampleCount.incrementAndGet();
        webrtcLossMilliSum.addAndGet(Math.round(packetLossPct * 1000));
        webrtcRttSum.addAndGet(rttMs);
        webrtcJitterMilliSum.addAndGet(Math.round(jitterMs * 1000));
    }

    private void markDeliveryIssueStart() {
        long now = System.currentTimeMillis();
        if (deliveryFirstIssueAtMs == null) {
            deliveryFirstIssueAtMs = now;
        }
        if (activeDeliveryOutageStartedMs == null) {
            activeDeliveryOutageStartedMs = now;
        }
    }

    private static long parseRecoveryMs(String detail) {
        if (detail == null || detail.isBlank()) {
            return 0;
        }
        try {
            return Long.parseLong(detail.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    List<ViewerSessionQoSDto> viewerSessionDtos() {
        return viewers.values().stream()
                .sorted(Comparator.comparing(ViewerSessionState::presenceId))
                .map(ViewerSessionState::toDto)
                .toList();
    }

    double avgPacketLossPct() {
        int n = webrtcStatsSampleCount.get();
        return n == 0 ? 0 : (webrtcLossMilliSum.get() / 1000.0) / n;
    }

    long avgRttMs() {
        int n = webrtcStatsSampleCount.get();
        return n == 0 ? 0 : webrtcRttSum.get() / n;
    }

    double avgJitterMs() {
        int n = webrtcStatsSampleCount.get();
        return n == 0 ? 0 : (webrtcJitterMilliSum.get() / 1000.0) / n;
    }

    long deliveryMttdMs() {
        if (deliveryFirstIssueAtMs == null) {
            return -1;
        }
        return deliveryFirstIssueAtMs - startedAtMs;
    }

    long deliveryMttrMs() {
        if (deliveryRecoveryCount.get() == 0) {
            return -1;
        }
        return deliveryRecoveryTotalMs.get() / deliveryRecoveryCount.get();
    }

    int ingestHealthScore(long minKbps) {
        int score = 100;
        if (ingestKbpsWindow.count() == 0 && currentIngestKbps == 0) {
            return 50;
        }
        long avg = ingestKbpsWindow.average();
        if (avg < minKbps) {
            score -= 25;
        }
        if (ingestUnstable.get()) {
            score -= 30;
        }
        score -= Math.min(20, ingestStallCount.get() * 5);
        double std = ingestKbpsWindow.stdDev();
        if (std > avg * 0.4 && avg > 0) {
            score -= 10;
        }
        return Math.max(0, Math.min(100, score));
    }

    int encodeHealthScore() {
        int score = 100;
        if (encodeSlow) {
            score -= 25;
        }
        if (encodeSpeedRatio < 0.8) {
            score -= 15;
        }
        score -= Math.min(25, encodeRestartCount.get() * 8);
        score -= Math.min(15, encodeErrorCount.get() * 5);
        return Math.max(0, Math.min(100, score));
    }

    int viewerHealthScore() {
        int joins = viewerJoinOk.get() + viewerJoinFail.get();
        if (joins == 0) {
            return 100;
        }
        int score = 100;
        double failRate = (double) viewerJoinFail.get() / joins;
        score -= (int) (failRate * 40);
        score -= Math.min(30, deliveryStallCount.get() * 3);
        score -= Math.min(20, viewerFatalCount.get() * 10);
        return Math.max(0, Math.min(100, score));
    }

    int overallHealthScore(long minIngestKbps) {
        int ingest = ingestHealthScore(minIngestKbps);
        int encode = encodeHealthScore();
        int viewer = viewerHealthScore();
        return (ingest * 45 + encode * 30 + viewer * 25) / 100;
    }

    long ingestKbpsAvg() {
        return ingestKbpsWindow.average();
    }

    long ingestKbpsP95() {
        return ingestKbpsWindow.p95();
    }

    double ingestKbpsStdDev() {
        return ingestKbpsWindow.stdDev();
    }

    void markZombieStop() {
        zombieStop = true;
        addEvent(QoSEvent.of(QoSStage.OPS, QoSEventType.STREAM_ZOMBIE_STOP, "Broadcaster heartbeat timeout"));
    }

    void finalizeSession() {
        if (finalized) {
            return;
        }
        finalized = true;
        endedAtMs = System.currentTimeMillis();
        if (ingestIssueStartedMs != null) {
            ingestStallTotalMs.addAndGet(endedAtMs - ingestIssueStartedMs);
            ingestIssueStartedMs = null;
        }
        for (ViewerSessionState vs : viewers.values()) {
            vs.close();
            viewerWatchMsTotal.addAndGet(vs.watchDurationMs());
        }
        addEvent(QoSEvent.of(QoSStage.OPS, QoSEventType.STREAM_STOPPED, "Stream session ended"));
    }

    boolean isFinalized() {
        return finalized;
    }

    Long endedAtMs() {
        return endedAtMs;
    }

    boolean zombieStop() {
        return zombieStop;
    }

    int ingestStallCount() {
        return ingestStallCount.get();
    }

    long ingestStallTotalMs() {
        return ingestStallTotalMs.get();
    }

    int ingestRecoveryCount() {
        return ingestRecoveryCount.get();
    }

    long ingestRecoveryTotalMs() {
        return ingestRecoveryTotalMs.get();
    }

    boolean ingestUnstable() {
        return ingestUnstable.get();
    }

    long currentIngestKbps() {
        return currentIngestKbps;
    }

    double encodeFpsActual() {
        return encodeFpsActual;
    }

    int encodeTargetFps() {
        return encodeTargetFps;
    }

    void setEncodeTargetFps(int fps) {
        this.encodeTargetFps = fps;
    }

    double encodeSpeedRatio() {
        return encodeSpeedRatio;
    }

    long encodeDroppedFrames() {
        return encodeDroppedFrames.get();
    }

    long encodeDuplicatedFrames() {
        return encodeDuplicatedFrames.get();
    }

    int encodeRestartCount() {
        return encodeRestartCount.get();
    }

    int encodeErrorCount() {
        return encodeErrorCount.get();
    }

    int deliveryReconnectCount() {
        return deliveryReconnectCount.get();
    }

    int deliveryAbrDownCount() {
        return deliveryAbrDownCount.get();
    }

    int deliveryAbrUpCount() {
        return deliveryAbrUpCount.get();
    }

    int deliveryStallCount() {
        return deliveryStallCount.get();
    }

    long avgStartupMs() {
        int n = deliveryStartupCount.get();
        return n == 0 ? 0 : deliveryStartupMsTotal.get() / n;
    }

    int viewerJoinOk() {
        return viewerJoinOk.get();
    }

    int viewerJoinFail() {
        return viewerJoinFail.get();
    }

    int viewerFatalCount() {
        return viewerFatalCount.get();
    }

    int concurrentViewerSessions() {
        return viewers.size();
    }

    double viewerStallsPerHour() {
        long watchMs = viewerWatchMsTotal.get();
        if (watchMs < 1000) {
            return deliveryStallCount.get();
        }
        double hours = watchMs / 3_600_000.0;
        return deliveryStallCount.get() / Math.max(0.01, hours);
    }

    long mttdMs() {
        synchronized (events) {
            for (QoSEvent e : events) {
                if (e.type() == QoSEventType.INGEST_UNSTABLE || e.type() == QoSEventType.INGEST_STALL) {
                    return e.atEpochMs() - startedAtMs;
                }
            }
        }
        return -1;
    }

    long mttrMs() {
        if (ingestRecoveryCount.get() == 0) {
            return -1;
        }
        return ingestRecoveryTotalMs.get() / ingestRecoveryCount.get();
    }

    long viewerWatchMsTotal() {
        return viewerWatchMsTotal.get();
    }

    static final class ViewerSessionState {
        private final String presenceId;
        private final long joinedAtMs;
        private volatile long closedAtMs;
        private final AtomicInteger qualitySwitchCount = new AtomicInteger(0);
        private final AtomicInteger stallCount = new AtomicInteger(0);
        private final AtomicInteger fatalCount = new AtomicInteger(0);
        private volatile long ttffMs = -1;
        private volatile String qualityLabel = "—";
        private volatile double packetLossPct;
        private volatile long rttMs;
        private volatile double jitterMs;
        private volatile long downloadKbps;

        ViewerSessionState(String presenceId) {
            this.presenceId = presenceId;
            this.joinedAtMs = System.currentTimeMillis();
        }

        void apply(QoSEventType type, String detail) {
            switch (type) {
                case DELIVERY_ABR_STEP_DOWN, DELIVERY_ABR_STEP_UP -> {
                    qualitySwitchCount.incrementAndGet();
                    if (detail != null && detail.contains("to ")) {
                        qualityLabel = detail.substring(detail.indexOf("to ") + 3).trim();
                    }
                }
                case VIEWER_STALL, DELIVERY_STALL -> stallCount.incrementAndGet();
                default -> {
                    // no-op
                }
            }
        }

        void updateStats(String label, double loss, long rtt, double jitter, long kbps) {
            if (label != null && !label.isBlank()) {
                qualityLabel = label;
            }
            packetLossPct = loss;
            rttMs = rtt;
            jitterMs = jitter;
            downloadKbps = kbps;
        }

        void close() {
            if (closedAtMs == 0) {
                closedAtMs = System.currentTimeMillis();
            }
        }

        long watchDurationMs() {
            long end = closedAtMs > 0 ? closedAtMs : System.currentTimeMillis();
            return Math.max(0, end - joinedAtMs);
        }

        String presenceId() {
            return presenceId;
        }

        ViewerSessionQoSDto toDto() {
            String shortId = presenceId.length() > 8 ? presenceId.substring(0, 8) + "…" : presenceId;
            return new ViewerSessionQoSDto(
                    shortId,
                    qualityLabel,
                    watchDurationMs(),
                    ttffMs,
                    stallCount.get(),
                    fatalCount.get(),
                    qualitySwitchCount.get(),
                    packetLossPct,
                    rttMs,
                    jitterMs,
                    downloadKbps);
        }
    }
}
