package com.livestream.realtime;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.api.dto.RoomSnapshot;
import com.livestream.model.LiveStream;
import com.livestream.model.StreamStatus;
import com.livestream.redis.RedisService;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * Live room: viewer presence (HTTP heartbeat), likes, active coupon. No chat.
 * When Redis is enabled, presence/likes/coupon are shared across app instances (Phase 5).
 */
@Singleton
public class LiveRoomHub {

    private static final long SESSION_TIMEOUT_MS = 30_000;

    private final SessionFactory sessionFactory;
    private final RedisService redisService;
    private final ConcurrentMap<Long, Room> rooms = new ConcurrentHashMap<>();

    @Inject
    public LiveRoomHub(SessionFactory sessionFactory, RedisService redisService) {
        this.sessionFactory = sessionFactory;
        this.redisService = redisService;
    }

    public boolean isStreamBroadcasting(long streamId) {
        StreamStatus status = streamStatus(streamId);
        return status == StreamStatus.LIVE || status == StreamStatus.PAUSED;
    }

    public StreamStatus streamStatus(long streamId) {
        if (redisService.isEnabled()) {
            return redisService.getStreamStatus(streamId).orElseGet(() -> loadAndCacheStatus(streamId));
        }
        return loadStatusFromDb(streamId);
    }

    public String join(long streamId) {
        requireLive(streamId);
        String presenceId = UUID.randomUUID().toString();
        if (redisService.isEnabled()) {
            redisService.viewerJoin(streamId, presenceId);
        } else {
            roomFor(streamId).touch(presenceId);
        }
        return presenceId;
    }

    public void heartbeat(long streamId, String presenceId) {
        requireLive(streamId);
        if (redisService.isEnabled()) {
            if (!redisService.viewerHeartbeat(streamId, presenceId)) {
                throw new IllegalArgumentException("Invalid presence session");
            }
            return;
        }
        Room room = rooms.get(streamId);
        if (room == null) {
            throw new IllegalArgumentException("Not in room — join first");
        }
        if (!room.touch(presenceId)) {
            throw new IllegalArgumentException("Invalid presence session");
        }
    }

    public void leave(long streamId, String presenceId) {
        if (redisService.isEnabled()) {
            redisService.viewerLeave(streamId, presenceId);
            return;
        }
        Room room = rooms.get(streamId);
        if (room != null) {
            room.sessions.remove(presenceId);
        }
    }

    public void recordLike(long streamId) {
        requireLive(streamId);
        if (redisService.isEnabled()) {
            redisService.likeIncr(streamId);
        } else {
            roomFor(streamId).likes.incrementAndGet();
        }
    }

    public void dropCoupon(long streamId, String code, int percentOff, int durationSec) {
        requireLive(streamId);
        long expiresAt = System.currentTimeMillis() + (durationSec * 1000L);
        ActiveCoupon coupon = new ActiveCoupon(code, percentOff, expiresAt);
        if (redisService.isEnabled()) {
            redisService.setCoupon(streamId, coupon);
        } else {
            roomFor(streamId).coupon = coupon;
        }
    }

    public RoomSnapshot snapshot(long streamId) {
        if (redisService.isEnabled()) {
            return redisService
                    .readRoomSnapshot(streamId, SESSION_TIMEOUT_MS)
                    .map(read -> new RoomSnapshot(
                            read.viewers(),
                            read.likes(),
                            read.coupon(),
                            read.status().name()))
                    .orElseGet(() -> new RoomSnapshot(0, 0, null, streamStatus(streamId).name()));
        }
        Room room = rooms.get(streamId);
        if (room == null) {
            return new RoomSnapshot(0, 0, null, streamStatus(streamId).name());
        }
        return room.snapshot(streamStatus(streamId).name());
    }

    public void closeRoom(long streamId) {
        if (redisService.isEnabled()) {
            redisService.deleteRoom(streamId);
        }
        rooms.remove(streamId);
    }

    public int viewersForStream(long streamId) {
        if (redisService.isEnabled()) {
            return redisService.viewerCount(streamId, SESSION_TIMEOUT_MS);
        }
        Room room = rooms.get(streamId);
        return room == null ? 0 : room.pruneAndCount();
    }

    public int totalViewers() {
        if (redisService.isEnabled()) {
            return redisService.totalViewerCount(SESSION_TIMEOUT_MS);
        }
        int total = 0;
        for (Room room : rooms.values()) {
            total += room.pruneAndCount();
        }
        return total;
    }

    private void requireLive(long streamId) {
        if (!isStreamBroadcasting(streamId)) {
            throw new IllegalStateException("Stream is not active");
        }
    }

    private StreamStatus loadAndCacheStatus(long streamId) {
        StreamStatus status = loadStatusFromDb(streamId);
        if (status == StreamStatus.LIVE || status == StreamStatus.PAUSED) {
            redisService.setStreamStatus(streamId, status);
        }
        return status;
    }

    private StreamStatus loadStatusFromDb(long streamId) {
        try (Session session = sessionFactory.openSession()) {
            LiveStream stream = session.find(LiveStream.class, streamId);
            return stream != null ? stream.getStatus() : StreamStatus.ENDED;
        }
    }

    private Room roomFor(long streamId) {
        return rooms.computeIfAbsent(streamId, id -> new Room());
    }

    private static final class Room {
        private final ConcurrentMap<String, Long> sessions = new ConcurrentHashMap<>();
        private final AtomicInteger likes = new AtomicInteger();
        private volatile ActiveCoupon coupon;

        boolean touch(String presenceId) {
            sessions.put(presenceId, System.currentTimeMillis());
            return true;
        }

        int pruneAndCount() {
            long cutoff = System.currentTimeMillis() - SESSION_TIMEOUT_MS;
            sessions.entrySet().removeIf(e -> e.getValue() < cutoff);
            return sessions.size();
        }

        RoomSnapshot snapshot(String streamStatus) {
            ActiveCoupon active = coupon;
            if (active != null && active.isExpired()) {
                coupon = null;
                active = null;
            }
            return new RoomSnapshot(pruneAndCount(), likes.get(), active, streamStatus);
        }
    }
}
