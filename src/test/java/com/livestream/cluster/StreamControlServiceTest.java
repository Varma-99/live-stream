package com.livestream.cluster;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Provider;
import com.livestream.api.dto.StreamResponse;
import com.livestream.redis.RedisService;
import com.livestream.service.StreamService;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StreamControlServiceTest {

    @Mock
    private RedisService redisService;

    @Mock
    private PeerInstanceClient peerInstanceClient;

    @Mock
    private StreamService streamService;

    @Mock
    private Provider<StreamService> streamServiceProvider;

    private StreamControlService controlService;

    @BeforeEach
    void setUp() {
        controlService = new StreamControlService(redisService, peerInstanceClient, streamServiceProvider);
    }

    @Test
    void stopStream_localWhenOwnerIsSelf() {
        when(streamServiceProvider.get()).thenReturn(streamService);
        StreamResponse ended = new StreamResponse();
        when(redisService.isEnabled()).thenReturn(true);
        when(redisService.tryAcquireStreamLock(42L)).thenReturn(true);
        when(redisService.getInstanceId()).thenReturn("app-1");
        when(redisService.getStreamOwner(42L)).thenReturn(Optional.of("app-1"));
        when(streamService.executeLocalStop(42L)).thenReturn(ended);

        StreamResponse result = controlService.stopStream(42L);

        assertEquals(ended, result);
        verify(streamService).executeLocalStop(42L);
        verify(peerInstanceClient, never()).forwardStop(eq("app-1"), anyLong());
        verify(redisService).releaseStreamLock(42L);
    }

    @Test
    void stopStream_forwardsWhenOwnerIsPeer() {
        StreamResponse ended = new StreamResponse();
        when(redisService.isEnabled()).thenReturn(true);
        when(redisService.tryAcquireStreamLock(7L)).thenReturn(true);
        when(redisService.getInstanceId()).thenReturn("app-2");
        when(redisService.getStreamOwner(7L)).thenReturn(Optional.of("app-1"));
        when(peerInstanceClient.forwardStop("app-1", 7L)).thenReturn(ended);

        StreamResponse result = controlService.stopStream(7L);

        assertEquals(ended, result);
        verify(peerInstanceClient).forwardStop("app-1", 7L);
        verify(streamService, never()).executeLocalStop(7L);
    }

    @Test
    void stopStream_rejectsWhenLockHeld() {
        when(redisService.isEnabled()).thenReturn(true);
        when(redisService.tryAcquireStreamLock(1L)).thenReturn(false);

        assertThrows(IllegalStateException.class, () -> controlService.stopStream(1L));
        verify(streamService, never()).executeLocalStop(1L);
    }
}
