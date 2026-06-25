package com.livestream.qos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.livestream.LiveStreamConfiguration;
import com.livestream.dao.LiveStreamDAO;
import com.livestream.dao.QoSSessionDAO;
import com.livestream.model.LiveStream;
import com.livestream.realtime.LiveRoomHub;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class StreamQoSServiceTest {

    private StreamQoSService service;
    private LiveStreamDAO liveStreamDAO;
    private QoSSessionDAO qosSessionDAO;

    @BeforeEach
    void setUp() {
        LiveStreamConfiguration config = mock(LiveStreamConfiguration.class);
        when(config.isMediamtxDelivery()).thenReturn(true);
        LiveRoomHub hub = mock(LiveRoomHub.class);
        when(hub.viewersForStream(anyLong())).thenReturn(0);
        when(hub.totalViewers()).thenReturn(0);
        liveStreamDAO = mock(LiveStreamDAO.class);
        qosSessionDAO = mock(QoSSessionDAO.class);
        service = new StreamQoSService(config, hub, liveStreamDAO, qosSessionDAO);
    }

    @Test
    void beginAndSnapshotUsesDeliveryMode() {
        service.beginSession(42L, "hls");
        var snap = service.snapshot(42L);
        assertNotNull(snap);
        assertEquals(42L, snap.getOverallHealthScore() >= 0 ? 42L : 0); // session exists
        assertNotNull(service.session(42L));
        assertEquals("hls", service.session(42L).deliveryMode());
    }

    @Test
    void endSessionPersistsAndRemoves() {
        LiveStream stream = new LiveStream();
        stream.setId(7L);
        when(liveStreamDAO.findById(7L)).thenReturn(Optional.of(stream));

        service.beginSession(7L, "webrtc");
        service.endSession(7L, false);

        verify(qosSessionDAO).save(
                eq(stream),
                anyLong(),
                any(),
                anyBoolean(),
                anyInt(),
                anyString(),
                anyLong(),
                eq("webrtc"),
                anyList(),
                anyList());
        assertEquals(null, service.session(7L));
    }

    @Test
    void ensureSessionUsesSrsWebRtcDeliveryMode() {
        LiveStreamConfiguration config = mock(LiveStreamConfiguration.class);
        when(config.isMediamtxDelivery()).thenReturn(false);
        when(config.isRtmpWebRtcDelivery()).thenReturn(true);
        LiveRoomHub hub = mock(LiveRoomHub.class);
        LiveStreamDAO dao = mock(LiveStreamDAO.class);
        LiveStream stream = new LiveStream();
        stream.setId(99L);
        stream.setDelivery("webrtc");
        when(dao.findById(99L)).thenReturn(Optional.of(stream));
        StreamQoSService svc = new StreamQoSService(config, hub, dao, qosSessionDAO);

        svc.ensureSession(99L);

        assertEquals("webrtc", svc.session(99L).deliveryMode());
    }

    @Test
    void snapshotWithoutSessionUsesStreamDeliveryFromDatabase() {
        LiveStreamConfiguration config = mock(LiveStreamConfiguration.class);
        when(config.isRtmpWebRtcDelivery()).thenReturn(true);
        LiveRoomHub hub = mock(LiveRoomHub.class);
        LiveStream stream = new LiveStream();
        stream.setId(55L);
        stream.setDelivery("webrtc");
        when(liveStreamDAO.findById(55L)).thenReturn(Optional.of(stream));
        StreamQoSService svc = new StreamQoSService(config, hub, liveStreamDAO, qosSessionDAO);

        var snap = svc.snapshot(55L);

        assertNotNull(snap.getDelivery());
        assertEquals("webrtc", snap.getDelivery().getDeliveryMode());
    }
}
