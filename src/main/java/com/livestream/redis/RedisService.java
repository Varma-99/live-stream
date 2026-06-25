package com.livestream.redis;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.livestream.LiveStreamConfiguration;
import com.livestream.config.RedisSettings;
import com.livestream.model.StreamStatus;
import com.livestream.realtime.ActiveCoupon;
import io.dropwizard.lifecycle.Managed;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.DefaultJedisClientConfig;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.ScanParams;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.ScanResult;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;

/**
 * Jedis connection pool for shared multi-instance state (Phase 3+).
 * No-ops when {@link LiveStreamConfiguration#isRedisEnabled()} is false.
 */
@Singleton
public class RedisService implements Managed {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisService.class);
    private static final String VIEWERS_KEY_PREFIX = "room:";
    private static final String VIEWERS_KEY_SUFFIX = ":viewers";
    static final int BROADCASTER_HEARTBEAT_TTL_SEC = 20;
    static final int STREAM_OWNER_TTL_SEC = 30;
    static final int STREAM_LOCK_TTL_SEC = 15;
    static final long ZOMBIE_CHECK_GRACE_MS = 30_000L;

    private final LiveStreamConfiguration configuration;
    private JedisPool pool;
    private volatile long zombieCheckGraceUntilMs = 0;
    private volatile boolean redisHadFailure;

    @Inject
    public RedisService(LiveStreamConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public void start() {
        if (!configuration.isRedisEnabled()) {
            LOGGER.info("Redis disabled — in-memory mode only");
            return;
        }
        RedisSettings settings = configuration.getRedis();
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(settings.getPoolMaxTotal());
        poolConfig.setMaxIdle(settings.getPoolMaxIdle());
        poolConfig.setMinIdle(settings.getPoolMinIdle());
        poolConfig.setMaxWaitMillis(3_000);

        String password = settings.getPassword();
        DefaultJedisClientConfig clientConfig = DefaultJedisClientConfig.builder()
                .connectionTimeoutMillis(settings.getTimeoutMs())
                .socketTimeoutMillis(settings.getTimeoutMs())
                .password(password == null || password.isBlank() ? null : password)
                .build();

        pool = new JedisPool(poolConfig, new HostAndPort(settings.getHost(), settings.getPort()), clientConfig);
        LOGGER.info(
                "Redis pool started — {}:{} pool={} (instanceId={})",
                settings.getHost(),
                settings.getPort(),
                settings.getPoolMaxTotal(),
                configuration.getInstanceId());
    }

    @Override
    public void stop() {
        if (pool != null) {
            pool.close();
            pool = null;
            LOGGER.info("Redis pool closed");
        }
    }

    public boolean isEnabled() {
        return configuration.isRedisEnabled() && pool != null;
    }

    public String getInstanceId() {
        return configuration.getInstanceId();
    }

    /** After Redis outage/restart, skip zombie checks until grace expires. */
    public boolean isZombieCheckGracePeriod() {
        return isEnabled() && System.currentTimeMillis() < zombieCheckGraceUntilMs;
    }

    private void extendZombieCheckGrace() {
        zombieCheckGraceUntilMs = System.currentTimeMillis() + ZOMBIE_CHECK_GRACE_MS;
    }

    /** PING → PONG when Redis is enabled and reachable. */
    public boolean ping() {
        return withJedis(j -> "PONG".equals(j.ping())).orElse(false);
    }

    /** Cache stream lifecycle status for hot-path reads (join/heartbeat). */
    public void setStreamStatus(long streamId, StreamStatus status) {
        if (!isEnabled() || status == null) {
            return;
        }
        withJedis(j -> {
            j.set(streamStatusKey(streamId), status.name());
            return null;
        });
    }

    public Optional<StreamStatus> getStreamStatus(long streamId) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        return withJedis(j -> j.get(streamStatusKey(streamId))).flatMap(RedisService::parseStreamStatus);
    }

    private static String streamStatusKey(long streamId) {
        return "stream:status:" + streamId;
    }

    private static Optional<StreamStatus> parseStreamStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(StreamStatus.valueOf(raw.trim()));
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid stream status in Redis: {}", raw);
            return Optional.empty();
        }
    }

    public void broadcasterHeartbeatTouch(long streamId) {
        if (!isEnabled()) {
            return;
        }
        withJedis(j -> {
            j.setex(broadcasterHeartbeatKey(streamId), BROADCASTER_HEARTBEAT_TTL_SEC, "1");
            return null;
        });
    }

    /**
     * True when heartbeat key exists. On Redis errors returns true (do not zombie-stop on blips).
     */
    public boolean broadcasterHeartbeatAlive(long streamId) {
        if (!isEnabled()) {
            return true;
        }
        return withJedis(j -> j.exists(broadcasterHeartbeatKey(streamId))).orElse(true);
    }

    public void broadcasterHeartbeatDelete(long streamId) {
        if (!isEnabled()) {
            return;
        }
        withJedis(j -> {
            j.del(broadcasterHeartbeatKey(streamId));
            return null;
        });
    }

    public void setStreamOwner(long streamId, String instanceId) {
        if (!isEnabled() || instanceId == null || instanceId.isBlank()) {
            return;
        }
        withJedis(j -> {
            j.setex(streamOwnerKey(streamId), STREAM_OWNER_TTL_SEC, instanceId);
            return null;
        });
    }

    public void refreshStreamOwner(long streamId) {
        if (!isEnabled()) {
            return;
        }
        withJedis(j -> {
            j.expire(streamOwnerKey(streamId), STREAM_OWNER_TTL_SEC);
            return null;
        });
    }

    public Optional<String> getStreamOwner(long streamId) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        return withJedis(j -> j.get(streamOwnerKey(streamId))).filter(s -> !s.isBlank());
    }

    public void deleteStreamOwner(long streamId) {
        if (!isEnabled()) {
            return;
        }
        withJedis(j -> {
            j.del(streamOwnerKey(streamId));
            return null;
        });
    }

    private static String broadcasterHeartbeatKey(long streamId) {
        return "broadcaster:heartbeat:" + streamId;
    }

    private static String streamOwnerKey(long streamId) {
        return "stream:owner:" + streamId;
    }

    /** SET stream:lock:{id} NX EX — returns false if another instance holds the lock. */
    public boolean tryAcquireStreamLock(long streamId) {
        if (!isEnabled()) {
            return true;
        }
        String instanceId = configuration.getInstanceId();
        return withJedis(j -> {
            SetParams params = SetParams.setParams().nx().ex(STREAM_LOCK_TTL_SEC);
            return "OK".equals(j.set(streamLockKey(streamId), instanceId, params));
        }).orElse(true);
    }

    public void releaseStreamLock(long streamId) {
        if (!isEnabled()) {
            return;
        }
        String instanceId = configuration.getInstanceId();
        withJedis(j -> {
            String holder = j.get(streamLockKey(streamId));
            if (instanceId.equals(holder)) {
                j.del(streamLockKey(streamId));
            }
            return null;
        });
    }

    /** Prevents concurrent start for the same broadcaster across instances. */
    public boolean tryAcquireBroadcasterStartLock(long broadcasterId) {
        if (!isEnabled()) {
            return true;
        }
        String instanceId = configuration.getInstanceId();
        return withJedis(j -> {
            SetParams params = SetParams.setParams().nx().ex(STREAM_LOCK_TTL_SEC);
            return "OK".equals(j.set(broadcasterStartLockKey(broadcasterId), instanceId, params));
        }).orElse(true);
    }

    public void releaseBroadcasterStartLock(long broadcasterId) {
        if (!isEnabled()) {
            return;
        }
        String instanceId = configuration.getInstanceId();
        withJedis(j -> {
            String holder = j.get(broadcasterStartLockKey(broadcasterId));
            if (instanceId.equals(holder)) {
                j.del(broadcasterStartLockKey(broadcasterId));
            }
            return null;
        });
    }

    private static String streamLockKey(long streamId) {
        return "stream:lock:" + streamId;
    }

    private static String broadcasterStartLockKey(long broadcasterId) {
        return "broadcaster:lock:" + broadcasterId;
    }

    /** ZADD score = last heartbeat epoch ms. */
    public void viewerJoin(long streamId, String presenceId) {
        if (!isEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        withJedis(j -> {
            j.zadd(viewersKey(streamId), now, presenceId);
            return null;
        });
    }

    /** Returns false if presenceId was never joined. */
    public boolean viewerHeartbeat(long streamId, String presenceId) {
        if (!isEnabled()) {
            return false;
        }
        return withJedis(j -> {
            if (j.zscore(viewersKey(streamId), presenceId) == null) {
                return false;
            }
            j.zadd(viewersKey(streamId), System.currentTimeMillis(), presenceId);
            return true;
        }).orElse(false);
    }

    public void viewerLeave(long streamId, String presenceId) {
        if (!isEnabled()) {
            return;
        }
        withJedis(j -> {
            j.zrem(viewersKey(streamId), presenceId);
            return null;
        });
    }

    public int viewerCount(long streamId, long sessionTimeoutMs) {
        if (!isEnabled()) {
            return 0;
        }
        return withJedis(j -> countViewers(j, streamId, sessionTimeoutMs)).orElse(0);
    }

    /**
     * Single round-trip room read for join/heartbeat/room snapshot (hot path under load).
     */
    public Optional<RoomSnapshotRead> readRoomSnapshot(long streamId, long sessionTimeoutMs) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        return withJedis(j -> {
            long cutoff = System.currentTimeMillis() - sessionTimeoutMs;
            Pipeline pipe = j.pipelined();
            Response<String> statusResponse = pipe.get(streamStatusKey(streamId));
            Response<Long> viewersResponse =
                    pipe.zcount(viewersKey(streamId), cutoff, Double.POSITIVE_INFINITY);
            Response<String> likesResponse = pipe.get(likesKey(streamId));
            Response<java.util.Map<String, String>> couponResponse = pipe.hgetAll(couponKey(streamId));
            pipe.sync();

            StreamStatus status = parseStreamStatus(statusResponse.get()).orElse(StreamStatus.ENDED);
            int viewers = viewersResponse.get() != null ? viewersResponse.get().intValue() : 0;
            int likes = 0;
            String likesRaw = likesResponse.get();
            if (likesRaw != null) {
                try {
                    likes = Integer.parseInt(likesRaw);
                } catch (NumberFormatException ignored) {
                    likes = 0;
                }
            }
            ActiveCoupon coupon = parseCoupon(couponResponse.get()).orElse(null);
            if (coupon != null && coupon.isExpired()) {
                j.del(couponKey(streamId));
                coupon = null;
            }
            return new RoomSnapshotRead(viewers, likes, coupon, status);
        });
    }

    public record RoomSnapshotRead(int viewers, int likes, ActiveCoupon coupon, StreamStatus status) {}

    /** Dev-scale total across all rooms (SCAN room:*:viewers). */
    public int totalViewerCount(long sessionTimeoutMs) {
        if (!isEnabled()) {
            return 0;
        }
        return withJedis(j -> {
            AtomicInteger total = new AtomicInteger();
            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams params = new ScanParams().match(VIEWERS_KEY_PREFIX + "*" + VIEWERS_KEY_SUFFIX).count(100);
            do {
                ScanResult<String> scan = j.scan(cursor, params);
                for (String key : scan.getResult()) {
                    parseStreamIdFromViewersKey(key)
                            .ifPresent(id -> total.addAndGet(countViewers(j, id, sessionTimeoutMs)));
                }
                cursor = scan.getCursor();
            } while (!"0".equals(cursor));
            return total.get();
        }).orElse(0);
    }

    public int likeIncr(long streamId) {
        if (!isEnabled()) {
            return 0;
        }
        Long value = withJedis(j -> j.incr(likesKey(streamId))).orElse(0L);
        return value.intValue();
    }

    public int likeGet(long streamId) {
        if (!isEnabled()) {
            return 0;
        }
        return withJedis(j -> j.get(likesKey(streamId)))
                .map(v -> {
                    try {
                        return Integer.parseInt(v);
                    } catch (NumberFormatException e) {
                        return 0;
                    }
                })
                .orElse(0);
    }

    public void setCoupon(long streamId, ActiveCoupon coupon) {
        if (!isEnabled() || coupon == null) {
            return;
        }
        withJedis(j -> {
            j.hset(
                    couponKey(streamId),
                    Map.of(
                            "code", coupon.getCode(),
                            "percentOff", String.valueOf(coupon.getPercentOff()),
                            "expiresAt", String.valueOf(coupon.getExpiresAt())));
            return null;
        });
    }

    public Optional<ActiveCoupon> getCoupon(long streamId) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        Optional<ActiveCoupon> coupon = withJedis(j -> j.hgetAll(couponKey(streamId)))
                .flatMap(RedisService::parseCoupon);
        if (coupon.isPresent() && coupon.get().isExpired()) {
            withJedis(j -> {
                j.del(couponKey(streamId));
                return null;
            });
            return Optional.empty();
        }
        return coupon;
    }

    public void deleteRoom(long streamId) {
        if (!isEnabled()) {
            return;
        }
        withJedis(j -> {
            j.del(viewersKey(streamId), likesKey(streamId), couponKey(streamId));
            return null;
        });
    }

    private static int countViewers(Jedis j, long streamId, long sessionTimeoutMs) {
        long cutoff = System.currentTimeMillis() - sessionTimeoutMs;
        Long count = j.zcount(viewersKey(streamId), cutoff, Double.POSITIVE_INFINITY);
        return count != null ? count.intValue() : 0;
    }

    private static String viewersKey(long streamId) {
        return VIEWERS_KEY_PREFIX + streamId + VIEWERS_KEY_SUFFIX;
    }

    private static String likesKey(long streamId) {
        return "room:" + streamId + ":likes";
    }

    private static String couponKey(long streamId) {
        return "room:" + streamId + ":coupon";
    }

    private static Optional<Long> parseStreamIdFromViewersKey(String key) {
        if (key == null || !key.startsWith(VIEWERS_KEY_PREFIX) || !key.endsWith(VIEWERS_KEY_SUFFIX)) {
            return Optional.empty();
        }
        String idPart = key.substring(VIEWERS_KEY_PREFIX.length(), key.length() - VIEWERS_KEY_SUFFIX.length());
        try {
            return Optional.of(Long.parseLong(idPart));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static Optional<ActiveCoupon> parseCoupon(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(new ActiveCoupon(
                    fields.get("code"),
                    Integer.parseInt(fields.get("percentOff")),
                    Long.parseLong(fields.get("expiresAt"))));
        } catch (RuntimeException e) {
            LOGGER.warn("Invalid coupon hash in Redis: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Runs a Redis command when enabled. Logs and returns empty on failure
     * so callers can degrade gracefully.
     */
    public <T> Optional<T> withJedis(Function<Jedis, T> action) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        try (Jedis jedis = pool.getResource()) {
            T result = action.apply(jedis);
            if (redisHadFailure) {
                extendZombieCheckGrace();
                LOGGER.warn("Redis recovered — zombie checks paused for {}s", ZOMBIE_CHECK_GRACE_MS / 1000);
            }
            redisHadFailure = false;
            return Optional.ofNullable(result);
        } catch (RuntimeException e) {
            redisHadFailure = true;
            extendZombieCheckGrace();
            LOGGER.warn("Redis operation failed: {}", e.getMessage());
            return Optional.empty();
        }
    }
}
