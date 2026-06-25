package com.livestream.cluster;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.livestream.api.dto.StreamResponse;
import com.livestream.redis.RedisService;
import com.livestream.service.StreamService;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Distributed start/stop coordination: Redis locks and owner-forwarded stop (Phase 7).
 */
@Singleton
public class StreamControlService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StreamControlService.class);

    private final RedisService redisService;
    private final PeerInstanceClient peerInstanceClient;
    private final Provider<StreamService> streamService;

    @Inject
    public StreamControlService(
            RedisService redisService,
            PeerInstanceClient peerInstanceClient,
            Provider<StreamService> streamService) {
        this.redisService = redisService;
        this.peerInstanceClient = peerInstanceClient;
        this.streamService = streamService;
    }

    public StreamResponse stopStream(long streamId) {
        if (!redisService.isEnabled()) {
            return streamService.get().executeLocalStop(streamId);
        }

        if (!redisService.tryAcquireStreamLock(streamId)) {
            throw new IllegalStateException("Stream operation in progress");
        }

        try {
            String self = redisService.getInstanceId();
            Optional<String> owner = redisService.getStreamOwner(streamId);
            if (owner.isPresent() && !owner.get().equals(self)) {
                LOGGER.info("Forwarding stop for stream {} to owner {}", streamId, owner.get());
                return peerInstanceClient.forwardStop(owner.get(), streamId);
            }
            return streamService.get().executeLocalStop(streamId);
        } finally {
            redisService.releaseStreamLock(streamId);
        }
    }
}
