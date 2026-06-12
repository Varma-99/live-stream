package com.livestream.qos;

import java.time.Instant;

public record QoSEvent(
        long atEpochMs,
        QoSStage stage,
        QoSEventType type,
        String message,
        String detail) {

    static QoSEvent of(QoSStage stage, QoSEventType type, String message) {
        return new QoSEvent(Instant.now().toEpochMilli(), stage, type, message, null);
    }

    static QoSEvent of(QoSStage stage, QoSEventType type, String message, String detail) {
        return new QoSEvent(Instant.now().toEpochMilli(), stage, type, message, detail);
    }
}
