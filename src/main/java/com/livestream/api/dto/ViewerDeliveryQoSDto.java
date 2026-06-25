package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Delivery path metrics (WebRTC or HLS). */
public class ViewerDeliveryQoSDto {

    @JsonProperty
    private long startupTimeMs;

    @JsonProperty
    private int rebufferCount;

    @JsonProperty
    private int reconnectCount;

    @JsonProperty
    private int abrStepDownCount;

    @JsonProperty
    private int abrStepUpCount;

    @JsonProperty
    private String deliveryMode;

    @JsonProperty
    private double avgPacketLossPct;

    @JsonProperty
    private long avgRttMs;

    @JsonProperty
    private double avgJitterMs;

    @JsonProperty
    private int deliveryHealthScore;

    @JsonProperty
    private long avgDelayMs;

    public ViewerDeliveryQoSDto() {}

    public ViewerDeliveryQoSDto(
            long startupTimeMs,
            int rebufferCount,
            int reconnectCount,
            int abrStepDownCount,
            int abrStepUpCount,
            String deliveryMode) {
        this(startupTimeMs, rebufferCount, reconnectCount, abrStepDownCount, abrStepUpCount, deliveryMode, 0, 0, 0);
    }

    public ViewerDeliveryQoSDto(
            long startupTimeMs,
            int rebufferCount,
            int reconnectCount,
            int abrStepDownCount,
            int abrStepUpCount,
            String deliveryMode,
            double avgPacketLossPct,
            long avgRttMs,
            double avgJitterMs) {
        this.startupTimeMs = startupTimeMs;
        this.rebufferCount = rebufferCount;
        this.reconnectCount = reconnectCount;
        this.abrStepDownCount = abrStepDownCount;
        this.abrStepUpCount = abrStepUpCount;
        this.deliveryMode = deliveryMode;
        this.avgPacketLossPct = avgPacketLossPct;
        this.avgRttMs = avgRttMs;
        this.avgJitterMs = avgJitterMs;
    }

    public void setDeliveryHealthScore(int deliveryHealthScore) {
        this.deliveryHealthScore = deliveryHealthScore;
    }

    public int getDeliveryHealthScore() {
        return deliveryHealthScore;
    }

    public String getDeliveryMode() {
        return deliveryMode;
    }

    public long getAvgDelayMs() {
        return avgDelayMs;
    }

    public void setAvgDelayMs(long avgDelayMs) {
        this.avgDelayMs = avgDelayMs;
    }
}
