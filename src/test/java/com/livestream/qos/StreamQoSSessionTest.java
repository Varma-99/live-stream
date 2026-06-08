package com.livestream.qos;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StreamQoSSessionTest {

    @Test
    void recordsTtffAndStall() {
        StreamQoSSession session = new StreamQoSSession(1L);
        session.setDeliveryMode("hls");
        session.recordViewerEvent("p1", QoSEventType.VIEWER_JOIN_OK, "join", null);
        session.recordViewerEvent("p1", QoSEventType.VIEWER_TTFF, "ttff", "1200");
        session.recordViewerEvent("p1", QoSEventType.VIEWER_STALL, "stall", null);
        session.recordViewerEvent("p1", QoSEventType.DELIVERY_ABR_STEP_DOWN, "abr", "480p");
        assertEquals(1, session.deliveryStallCount());
        assertEquals(1, session.deliveryAbrDownCount());
        assertTrue(session.avgStartupMs() >= 1200);
        assertTrue(session.viewerHealthScore() < 100);
        assertEquals("hls", session.deliveryMode());
    }

    @Test
    void eventLogEvictsBeyondMax() {
        StreamQoSSession session = new StreamQoSSession(2L);
        for (int i = 0; i < 250; i++) {
            session.addEvent(QoSEvent.of(QoSStage.OPS, QoSEventType.ALERT_RAISED, "e" + i));
        }
        assertTrue(session.allEvents().size() <= 200);
    }

    @Test
    void overallHealthScoreWithinRange() {
        StreamQoSSession session = new StreamQoSSession(3L);
        session.recordIngestSample(2000, false, false);
        int score = session.overallHealthScore(300);
        assertTrue(score >= 0 && score <= 100);
    }

    @Test
    void webRtcStatsAggregate() {
        StreamQoSSession session = new StreamQoSSession(4L);
        session.recordWebRtcStats("p1", "720p", 2.5, 80, 12.0, 3000);
        session.recordWebRtcStats("p1", "720p", 1.0, 60, 8.0, 4000);
        assertTrue(session.avgPacketLossPct() > 0);
        assertTrue(session.avgRttMs() > 0);
    }

    @Test
    void loadLikeViewersStayAboveExperienceFloor() {
        StreamQoSSession session = new StreamQoSSession(5L);
        session.setDeliveryMode("webrtc");
        for (int i = 0; i < 100; i++) {
            String pid = "vu" + i;
            session.recordViewerEvent(pid, QoSEventType.VIEWER_JOIN_OK, "join", null);
            session.recordViewerEvent(pid, QoSEventType.VIEWER_TTFF, "ttff", "1500");
            if (i % 3 == 0) {
                session.recordViewerEvent(pid, QoSEventType.VIEWER_STALL, "stall", null);
            }
            if (i % 10 == 0) {
                session.recordViewerEvent(pid, QoSEventType.DELIVERY_ABR_STEP_DOWN, "abr", "480p");
            }
            session.recordWebRtcStats(pid, "720p", 1.2, 90, 10.0, 2500);
        }
        assertTrue(session.viewerHealthScore() >= 55);
    }

    @Test
    void hlsIngestUnknownDoesNotAliasEncode() {
        StreamQoSSession session = new StreamQoSSession(6L);
        session.setDeliveryMode("hls");
        session.recordEncodeRestart();
        session.recordEncodeRestart();
        assertEquals(100, session.ingestHealthScore(300));
        int overall = session.overallHealthScore(300);
        int encodeOnlyWeighted = (100 + session.encodeHealthScore() + 100 + 100) / 4;
        assertTrue(overall >= encodeOnlyWeighted - 5);
    }

    @Test
    void deliveryScoreIgnoresViewerStalls() {
        StreamQoSSession session = new StreamQoSSession(7L);
        session.setDeliveryMode("webrtc");
        session.recordViewerEvent("p1", QoSEventType.VIEWER_JOIN_OK, "join", null);
        int deliveryBefore = session.deliveryHealthScore();
        int viewerBefore = session.viewerHealthScore();
        for (int i = 0; i < 20; i++) {
            session.recordViewerEvent("p1", QoSEventType.VIEWER_STALL, "stall", null);
        }
        assertEquals(deliveryBefore, session.deliveryHealthScore());
        assertTrue(session.viewerHealthScore() < viewerBefore);
    }

    @Test
    void fatalPctCountsSessionsNotEvents() {
        StreamQoSSession session = new StreamQoSSession(8L);
        session.recordViewerEvent("p1", QoSEventType.VIEWER_JOIN_OK, "join", null);
        session.recordViewerEvent("p2", QoSEventType.VIEWER_JOIN_OK, "join", null);
        session.recordViewerEvent("p1", QoSEventType.VIEWER_FATAL, "fatal", null);
        session.recordViewerEvent("p1", QoSEventType.VIEWER_FATAL, "fatal", null);
        StreamQoSSession control = new StreamQoSSession(9L);
        control.recordViewerEvent("p1", QoSEventType.VIEWER_JOIN_OK, "join", null);
        control.recordViewerEvent("p2", QoSEventType.VIEWER_JOIN_OK, "join", null);
        control.recordViewerEvent("p1", QoSEventType.VIEWER_FATAL, "fatal", null);
        assertEquals(session.viewerHealthScore(), control.viewerHealthScore());
    }
}
