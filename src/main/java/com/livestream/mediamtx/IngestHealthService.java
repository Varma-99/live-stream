package com.livestream.mediamtx;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.LiveStreamConfiguration;
import com.livestream.model.LiveStream;
import com.livestream.qos.StreamQoSService;
import com.livestream.model.StreamStatus;
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
 * Polls MediaMTX ingest bytes for active streams and flags unstable upload.
 */
@Singleton
public class IngestHealthService implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(IngestHealthService.class);
    private static final long MIN_INGEST_KBPS = 300;
    private static final long STALL_MS = 5_000;

    private final LiveStreamConfiguration configuration;
    private final SessionFactory sessionFactory;
    private final MediamtxApiClient mediamtxApiClient;
    private final StreamQoSService streamQoSService;

    private final ConcurrentHashMap<Long, IngestSnapshot> snapshots = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, PathSample> lastSamples = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, SimulatedIngest> simulated = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;

    @Inject
    public IngestHealthService(
            LiveStreamConfiguration configuration,
            SessionFactory sessionFactory,
            MediamtxApiClient mediamtxApiClient,
            StreamQoSService streamQoSService) {
        this.configuration = configuration;
        this.sessionFactory = sessionFactory;
        this.mediamtxApiClient = mediamtxApiClient;
        this.streamQoSService = streamQoSService;
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
                                "Demo: simulated upload problem (not real network)")));
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
        if (!mediamtxApiClient.isEnabled()) {
            LOGGER.info("MediaMTX ingest health polling disabled (API off or not mediamtx mode)");
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ingest-health");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::poll, 2, 2, TimeUnit.SECONDS);
        LOGGER.info("MediaMTX ingest health polling every 2s");
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void poll() {
        try {
            Map<String, Long> pathBytes = mediamtxApiClient.pathBytesReceived();
            long now = System.currentTimeMillis();

            try (Session session = sessionFactory.openSession()) {
                var streams = session.createQuery(
                                "FROM LiveStream s WHERE s.status IN :statuses", LiveStream.class)
                        .setParameter(
                                "statuses",
                                java.util.EnumSet.of(StreamStatus.LIVE, StreamStatus.PAUSED))
                        .getResultList();

                for (LiveStream stream : streams) {
                    evaluateStream(stream, pathBytes, now);
                }
            }
        } catch (Exception e) {
            LOGGER.debug("Ingest health poll failed: {}", e.getMessage());
        }
    }

    private void evaluateStream(LiveStream stream, Map<String, Long> pathBytes, long now) {
        Long streamId = stream.getId();
        String pathName = primaryIngestPath(stream.getStreamKey());
        Long bytes = pathBytes.get(pathName);
        if (bytes == null) {
            snapshots.put(streamId, new IngestSnapshot(false, 0, true, "No ingest on MediaMTX"));
            streamQoSService.recordIngest(streamId, 0, false, false, true, "No ingest on MediaMTX");
            return;
        }

        PathSample previous = lastSamples.get(streamId);
        lastSamples.put(streamId, new PathSample(bytes, now));

        if (previous == null) {
            snapshots.put(streamId, new IngestSnapshot(true, 0, false, null));
            return;
        }

        long deltaBytes = bytes - previous.bytes();
        long deltaMs = Math.max(1, now - previous.atMs());
        long kbps = (deltaBytes * 8) / deltaMs;

        boolean stalled = deltaBytes <= 0 && (now - previous.atMs()) > STALL_MS;
        boolean unstable = stalled || kbps < MIN_INGEST_KBPS;
        String reason = stalled ? "Upload stalled" : (unstable ? "Upload bitrate low" : null);
        snapshots.put(streamId, new IngestSnapshot(true, kbps, unstable, reason));
        streamQoSService.recordIngest(streamId, kbps, true, stalled, unstable, reason);
    }

    private String primaryIngestPath(String streamKey) {
        if (configuration.isAbrEnabled()) {
            return "live/" + streamKey + "_high";
        }
        return "live/" + streamKey;
    }

    private record PathSample(long bytes, long atMs) {
    }

    public record IngestSnapshot(boolean monitoring, long ingestKbps, boolean unstable, String reason) {
        static IngestSnapshot unknown() {
            return new IngestSnapshot(false, 0, false, null);
        }
    }

    private record SimulatedIngest(long expiresAtMs, IngestSnapshot snapshot) {
    }
}
