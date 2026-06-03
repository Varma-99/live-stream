package com.livestream.qos;

/** Pipeline stage for QoS events and metrics. */
public enum QoSStage {
    INGEST,
    ENCODE,
    DELIVERY,
    VIEWER,
    OPS
}
