package com.livestream.realtime;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.api.dto.RoomSnapshot;
import com.livestream.model.LiveStream;
import com.livestream.model.StreamStatus;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;

/**
 * In-memory live room: viewer presence (HTTP heartbeat), likes, active coupon. No chat.
 */
@Singleton
public class LiveRoomHub {

    private static final long SESSION_TIMEOUT_MS = 30_000;

    private final SessionFactory sessionFactory;
    private final ConcurrentMap<Long, Room> rooms = new ConcurrentHashMap<>();

    @Inject
    public LiveRoomHub(SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    public boolean isStreamBroadcasting(long streamId) {
        try (Session session = sessionFactory.openSession()) {
            LiveStream stream = session.find(LiveStream.class, streamId);
            if (stream == null) {
                return false;
            }
            StreamStatus status = stream.getStatus();
            return status == StreamStatus.LIVE || status == StreamStatus.PAUSED;
        }
    }

    public StreamStatus streamStatus(long streamId) {
        try (Session session = sessionFactory.openSession()) {
            LiveStream stream = session.find(LiveStream.class, streamId);
            return stream != null ? stream.getStatus() : StreamStatus.ENDED;
        }
    }

    public String join(long streamId) {
        requireLive(streamId);
        Room room = roomFor(streamId);
        String presenceId = UUID.randomUUID().toString();
        room.touch(presenceId);
        return presenceId;
    }

    public void heartbeat(long streamId, String presenceId) {
        requireLive(streamId);
        Room room = rooms.get(streamId);
        if (room == null) {
            throw new IllegalArgumentException("Not in room — join first");
        }
        if (!room.touch(presenceId)) {
            throw new IllegalArgumentException("Invalid presence session");
        }
    }

    public void leave(long streamId, String presenceId) {
        Room room = rooms.get(streamId);
        if (room != null) {
            room.sessions.remove(presenceId);
        }
    }

    public void recordLike(long streamId) {
        requireLive(streamId);
        roomFor(streamId).likes.incrementAndGet();
    }

    public void dropCoupon(long streamId, String code, int percentOff, int durationSec) {
        requireLive(streamId);
        long expiresAt = System.currentTimeMillis() + (durationSec * 1000L);
        roomFor(streamId).coupon = new ActiveCoupon(code, percentOff, expiresAt);
    }

    public RoomSnapshot snapshot(long streamId) {
        String status = streamStatus(streamId).name();
        Room room = rooms.get(streamId);
        if (room == null) {
            return new RoomSnapshot(0, 0, null, status);
        }
        return room.snapshot(status);
    }

    public void closeRoom(long streamId) {
        rooms.remove(streamId);
    }

    private void requireLive(long streamId) {
        if (!isStreamBroadcasting(streamId)) {
            throw new IllegalStateException("Stream is not active");
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
