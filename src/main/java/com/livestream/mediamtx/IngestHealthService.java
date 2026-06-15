package com.livestream.mediamtx;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.LiveStreamConfiguration;
import com.livestream.model.LiveStream;
import com.livestream.model.StreamStatus;
import com.livestream.qos.StreamQoSService;
import com.livestream.service.VideoService;
import com.livestream.srs.SrsApiClient;
import com.livestream.srs.SrsPathStats;
import io.dropwizard.lifecycle.Managed;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polls MediaMTX or SRS ingest bytes for active streams and flags unstable upload.
 */
@Singleton
public class IngestHealthService implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestHealthService.class);
    private static final long MIN_INGEST_KBPS = 300;
    private static final long STALL_MS = 5_000;

    private final LiveStreamConfiguration configuration;
    private final SessionFactory sessionFactory;
    private final MediamtxApiClient mediamtxApiClient;
    private final SrsApiClient srsApiClient;
    private final StreamQoSService streamQoSService;
    private final VideoService videoService;

    private final ConcurrentHashMap<Long, IngestSnapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PathSample> lastSamples = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, SimulatedIngest> simulated = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;

    @Inject
    public IngestHealthService(
            LiveStreamConfiguration configuration,
            SessionFactory sessionFactory,
            MediamtxApiClient mediamtxApiClient,
            SrsApiClient srsApiClient,
            StreamQoSService streamQoSService,
            VideoService videoService) {
        this.configuration = configuration;
        this.sessionFactory = sessionFactory;
        this.mediamtxApiClient = mediamtxApiClient;
        this.srsApiClient = srsApiClient;
        this.streamQoSService = streamQoSService;
        this.videoService = videoService;
    }

    public IngestSnapshot snapshot(long streamId) {
        SimulatedIngest demo = simulated.get(streamId);
        if (demo != null) {
            if (System.currentTimeMillis() > demo.expiresAtMs()) {
                simulated.remove(streamId);
            } else {
                return demo.snapshot();
            }
        }
        return snapshots.getOrDefault(streamId, IngestSnapshot.unknown());
    }

    /** True when SRS/MediaMTX reports an active publish path for this stream (from last poll). */
    public boolean isPublishActive(long streamId) {
        return snapshot(streamId).publishActive();
    }

    /** Dev/demo: force unstable ingest metrics for UI testing (localhost RTMP is always fast). */
    public void simulateUnstable(long streamId, int durationSec) {
        int seconds = Math.max(10, Math.min(durationSec, 300));
        long expiresAt = System.currentTimeMillis() + (seconds * 1000L);
        simulated.put(
                streamId,
                new SimulatedIngest(
                        expiresAt,
                        new IngestSnapshot(
                                true,
                                100,
                                true,
                                "Demo: simulated upload problem (not real network)",
                                true)));
        LOGGER.info("Ingest demo simulation on stream {} for {}s", streamId, seconds);
    }

    public void clearSimulation(long streamId) {
        simulated.remove(streamId);
    }

    public void clear(long streamId) {
        snapshots.remove(streamId);
        lastSamples.remove(streamId);
        simulated.remove(streamId);
    }

    @Override
    public void start() {
        if (!ingestPollingEnabled()) {
            if (configuration.isRtmpWebRtcDelivery()) {
                LOGGER.warn(
                        "Ingest health polling disabled — check API flags (srs: streamDelivery=srs + srsApiEnabled; "
                                + "mediamtx: streamDelivery=mediamtx + mediamtxApiEnabled)");
            } else {
                LOGGER.info("Ingest health polling disabled (HLS mode uses FFmpeg logs)");
            }
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ingest-health");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::poll, 2, 2, TimeUnit.SECONDS);
        String backend = configuration.isSrsDelivery() ? "SRS" : "MediaMTX";
        LOGGER.info("{} ingest health polling every 2s", backend);
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void poll() {
        try {
            long now = System.currentTimeMillis();
            Map<String, Long> pathBytes = null;
            Map<String, SrsPathStats> srsStats = null;
            if (srsApiClient.isEnabled()) {
                srsStats = srsApiClient.pathStats();
            } else {
                pathBytes = mediamtxApiClient.pathBytesReceived();
            }

            try (Session session = sessionFactory.openSession()) {
                var streams = session.createQuery(
                                "FROM LiveStream s WHERE s.status IN :statuses", LiveStream.class)
                        .setParameter(
                                "statuses",
                                java.util.EnumSet.of(StreamStatus.LIVE, StreamStatus.PAUSED))
                        .getResultList();

                for (LiveStream stream : streams) {
                    if (!videoService.isMediamtxDelivery(stream)) {
                        snapshots.remove(stream.getId());
                        lastSamples.remove(stream.getId());
                        continue;
                    }
                    if (srsStats != null) {
                        evaluateSrsStream(stream, srsStats, now);
                    } else {
                        evaluateStream(stream, pathBytes, now);
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Ingest health poll failed: {}", e.getMessage());
        }
    }

    private void evaluateStream(LiveStream stream, Map<String, Long> pathBytes, long now) {
        Long streamId = stream.getId();
        String pathName = videoService.ingestMonitorPath(stream);
        Long bytes = resolveIngestBytes(pathBytes, stream, pathName);
        if (bytes == null) {
            String msg = ingestMissMessage(pathBytes.isEmpty(), pathName);
            snapshots.put(streamId, new IngestSnapshot(false, 0, true, msg, false));
            streamQoSService.recordIngest(streamId, 0, false, false, true, msg);
            return;
        }

        PathSample previous = lastSamples.get(streamId);
        lastSamples.put(streamId, new PathSample(bytes, now));

        if (previous == null) {
            snapshots.put(streamId, new IngestSnapshot(true, 0, false, null, true));
            return;
        }

        long deltaBytes = Math.max(0, bytes - previous.bytes());
        long deltaMs = Math.max(1, now - previous.atMs());
        long kbps = (deltaBytes * 8) / deltaMs;

        boolean stalled = deltaBytes <= 0 && (now - previous.atMs()) > STALL_MS;
        boolean unstable = stalled || kbps < MIN_INGEST_KBPS;
        String reason = stalled ? "Upload stalled" : (unstable ? "Upload bitrate low" : null);
        snapshots.put(streamId, new IngestSnapshot(true, kbps, unstable, reason, true));
        streamQoSService.recordIngest(streamId, kbps, true, stalled, unstable, reason);
    }

    private boolean ingestPollingEnabled() {
        return srsApiClient.isEnabled() || mediamtxApiClient.isEnabled();
    }

    /** SRS: sum smoothed kbps + bytes across all ABR RTMP paths for this stream key. */
    private void evaluateSrsStream(LiveStream stream, Map<String, SrsPathStats> stats, long now) {
        Long streamId = stream.getId();
        String streamKey = stream.getStreamKey();
        long totalBytes = 0;
        long totalKbps = 0;
        boolean found = false;
        for (Map.Entry<String, SrsPathStats> entry : stats.entrySet()) {
            if (!matchesStreamIngestPath(entry.getKey(), streamKey, stream)) {
                continue;
            }
            found = true;
            totalBytes += entry.getValue().recvBytes();
            totalKbps += entry.getValue().recvKbps30s();
        }
        if (!found) {
            String pathName = videoService.ingestMonitorPath(stream);
            String msg = ingestMissMessage(stats.isEmpty(), pathName);
            snapshots.put(streamId, new IngestSnapshot(false, 0, true, msg, false));
            streamQoSService.recordIngest(streamId, 0, false, false, true, msg);
            return;
        }

        PathSample previous = lastSamples.get(streamId);
        lastSamples.put(streamId, new PathSample(totalBytes, now));

        boolean stalled = false;
        if (previous != null) {
            long deltaBytes = Math.max(0, totalBytes - previous.bytes());
            stalled = deltaBytes <= 0 && (now - previous.atMs()) > STALL_MS;
        }

        boolean unstable = stalled || (previous != null && totalKbps < MIN_INGEST_KBPS);
        String reason = stalled ? "Upload stalled" : (unstable ? "Upload bitrate low" : null);
        snapshots.put(streamId, new IngestSnapshot(true, totalKbps, unstable, reason, true));
        streamQoSService.recordIngest(streamId, totalKbps, true, stalled, unstable, reason);
    }

    private boolean matchesStreamIngestPath(String path, String streamKey, LiveStream stream) {
        if (!path.startsWith("live/")) {
            return false;
        }
        String name = path.substring("live/".length());
        if (name.equals(streamKey)) {
            return true;
        }
        if (!name.startsWith(streamKey + "_")) {
            return false;
        }
        if (!videoService.isAbrEnabled(stream)) {
            return true;
        }
        return name.endsWith("_high") || name.endsWith("_mid") || name.endsWith("_low");
    }

    private Long resolveIngestBytes(Map<String, Long> pathBytes, LiveStream stream, String pathName) {
        Long bytes = pathBytes.get(pathName);
        if (bytes != null) {
            return bytes;
        }
        String streamKey = stream.getStreamKey();
        Long best = null;
        for (Map.Entry<String, Long> entry : pathBytes.entrySet()) {
            if (!matchesStreamIngestPath(entry.getKey(), streamKey, stream)) {
                continue;
            }
            if (!videoService.isAbrEnabled(stream) || entry.getKey().endsWith("_high")) {
                best = entry.getValue();
                break;
            }
        }
        return best;
    }

    private String ingestMissMessage(boolean apiEmpty, String pathName) {
        if (apiEmpty) {
            if (configuration.isSrsDelivery()) {
                return "SRS API unreachable — is ./scripts/start-srs.sh running?";
            }
            return "MediaMTX API unreachable — is ./scripts/start-mediamtx.sh running?";
        }
        LOGGER.debug("Ingest path {} not in SRS/MediaMTX API", pathName);
        return "No ingest on media server (expected " + pathName + ")";
    }

    private record PathSample(long bytes, long atMs) {
    }

    public record IngestSnapshot(
            boolean monitoring, long ingestKbps, boolean unstable, String reason, boolean publishActive) {

        static IngestSnapshot unknown() {
            return new IngestSnapshot(false, 0, false, null, false);
        }
    }

    private record SimulatedIngest(long expiresAtMs, IngestSnapshot snapshot) {
    }
}
