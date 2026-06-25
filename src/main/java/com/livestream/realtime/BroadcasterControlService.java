package com.livestream.realtime;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.mediamtx.IngestHealthService;
import com.livestream.model.LiveStream;
import com.livestream.qos.StreamQoSService;
import com.livestream.model.StreamStatus;
import com.livestream.redis.RedisService;
import com.livestream.service.VideoService;
import io.dropwizard.lifecycle.Managed;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tracks streamer control-panel heartbeats. Stops FFmpeg if the broadcaster tab disappears.
 * With Redis: heartbeat is shared; zombie check runs only on the instance that owns FFmpeg.
 */
@Singleton
public class BroadcasterControlService implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(BroadcasterControlService.class);
    static final long HEARTBEAT_TIMEOUT_MS = 15_000;
    private static final long CHECK_INTERVAL_SECONDS = 5;
    private static final long OWNER_REFRESH_INTERVAL_SECONDS = 10;

    private final SessionFactory sessionFactory;
    private final VideoService videoService;
    private final LiveRoomHub liveRoomHub;
    private final IngestHealthService ingestHealthService;
    private final StreamQoSService streamQoSService;
    private final RedisService redisService;

    /** Local FFmpeg ownership — streams started on this JVM. */
    private final Set<Long> locallyOwnedStreams = ConcurrentHashMap.newKeySet();
    /** In-memory heartbeats when Redis is disabled. */
    private final ConcurrentHashMap<Long, Long> lastHeartbeatMs = new ConcurrentHashMap<>();
    private ScheduledExecutorService scheduler;

    @Inject
    public BroadcasterControlService(
            SessionFactory sessionFactory,
            VideoService videoService,
            LiveRoomHub liveRoomHub,
            IngestHealthService ingestHealthService,
            StreamQoSService streamQoSService,
            RedisService redisService) {
        this.sessionFactory = sessionFactory;
        this.videoService = videoService;
        this.liveRoomHub = liveRoomHub;
        this.ingestHealthService = ingestHealthService;
        this.streamQoSService = streamQoSService;
        this.redisService = redisService;
    }

    public void onStreamStarted(long streamId) {
        onStreamStarted(streamId, false);
    }

    /** Dummy streams skip zombie tracking — no broadcast.html heartbeat required. */
    public void onStreamStarted(long streamId, boolean dummy) {
        if (dummy) {
            return;
        }
        locallyOwnedStreams.add(streamId);
        if (redisService.isEnabled()) {
            redisService.broadcasterHeartbeatTouch(streamId);
        } else {
            lastHeartbeatMs.put(streamId, System.currentTimeMillis());
        }
    }

    public void touch(long streamId) {
        if (redisService.isEnabled()) {
            redisService.broadcasterHeartbeatTouch(streamId);
        } else {
            lastHeartbeatMs.put(streamId, System.currentTimeMillis());
        }
    }

    public void onStreamStopped(long streamId) {
        locallyOwnedStreams.remove(streamId);
        lastHeartbeatMs.remove(streamId);
        if (redisService.isEnabled()) {
            redisService.broadcasterHeartbeatDelete(streamId);
            redisService.deleteStreamOwner(streamId);
        }
    }

    public boolean isLocallyOwned(long streamId) {
        return locallyOwnedStreams.contains(streamId);
    }

    @Override
    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "broadcaster-control");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::checkTimeouts, CHECK_INTERVAL_SECONDS, CHECK_INTERVAL_SECONDS, TimeUnit.SECONDS);
        if (redisService.isEnabled()) {
            scheduler.scheduleAtFixedRate(
                    this::refreshOwnedStreams,
                    OWNER_REFRESH_INTERVAL_SECONDS,
                    OWNER_REFRESH_INTERVAL_SECONDS,
                    TimeUnit.SECONDS);
        }
        LOGGER.info("Broadcaster control: auto-stop if heartbeat missing for {}s", HEARTBEAT_TIMEOUT_MS / 1000);
    }

    @Override
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    private void refreshOwnedStreams() {
        String self = redisService.getInstanceId();
        for (long streamId : locallyOwnedStreams) {
            redisService.setStreamOwner(streamId, self);
        }
    }

    private void checkTimeouts() {
        if (redisService.isEnabled()) {
            if (redisService.isZombieCheckGracePeriod()) {
                return;
            }
            for (long streamId : locallyOwnedStreams) {
                if (!redisService.broadcasterHeartbeatAlive(streamId)) {
                    zombieStop(streamId, "broadcaster control heartbeat timeout (redis)");
                }
            }
            return;
        }
        long now = System.currentTimeMillis();
        for (var entry : lastHeartbeatMs.entrySet()) {
            long streamId = entry.getKey();
            if (now - entry.getValue() > HEARTBEAT_TIMEOUT_MS) {
                zombieStop(streamId, "broadcaster control heartbeat timeout");
            }
        }
    }

    private void zombieStop(long streamId, String reason) {
        if (redisService.isEnabled()) {
            if (!locallyOwnedStreams.contains(streamId)) {
                return;
            }
        } else if (!lastHeartbeatMs.containsKey(streamId)) {
            return;
        }
        locallyOwnedStreams.remove(streamId);
        lastHeartbeatMs.remove(streamId);

        try (Session session = sessionFactory.openSession()) {
            Transaction tx = session.beginTransaction();
            try {
                LiveStream stream = session.find(LiveStream.class, streamId);
                if (stream == null) {
                    tx.commit();
                    return;
                }
                StreamStatus status = stream.getStatus();
                if (status != StreamStatus.LIVE && status != StreamStatus.PAUSED) {
                    tx.commit();
                    return;
                }

                streamQoSService.endSession(streamId, true);
                videoService.stopAnyEncoderForStream(streamId);
                ingestHealthService.clear(streamId);
                liveRoomHub.closeRoom(streamId);
                stream.setStatus(StreamStatus.ENDED);
                stream.setEndedAt(Instant.now());

                tx.commit();
                redisService.setStreamStatus(streamId, StreamStatus.ENDED);
                redisService.broadcasterHeartbeatDelete(streamId);
                redisService.deleteStreamOwner(streamId);
                LOGGER.warn("Zombie stop stream {} ({})", streamId, reason);
            } catch (RuntimeException e) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                LOGGER.error("Zombie stop failed for stream {}", streamId, e);
            }
        }
    }
}
