package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Per-viewer QoS (one row per presence id). */
public class ViewerSessionQoSDto {

    @JsonProperty
    private String presenceId;

    @JsonProperty
    private String qualityLabel;

    @JsonProperty
    private long watchMs;

    @JsonProperty
    private long ttffMs;

    @JsonProperty
    private int stalls;

    @JsonProperty
    private int fatals;

    @JsonProperty
    private int qualitySwitches;

    @JsonProperty
    private double packetLossPct;

    @JsonProperty
    private long rttMs;

    @JsonProperty
    private double jitterMs;

    @JsonProperty
    private long downloadKbps;

    @JsonProperty
    private long avgDelayMs;

    public ViewerSessionQoSDto() {}

    public ViewerSessionQoSDto(
            String presenceId,
            String qualityLabel,
            long watchMs,
            long ttffMs,
            int stalls,
            int fatals,
            int qualitySwitches,
            double packetLossPct,
            long rttMs,
            double jitterMs,
            long downloadKbps,
            long avgDelayMs) {
        this.presenceId = presenceId;
        this.qualityLabel = qualityLabel;
        this.watchMs = watchMs;
        this.ttffMs = ttffMs;
        this.stalls = stalls;
        this.fatals = fatals;
        this.qualitySwitches = qualitySwitches;
        this.packetLossPct = packetLossPct;
        this.rttMs = rttMs;
        this.jitterMs = jitterMs;
        this.downloadKbps = downloadKbps;
        this.avgDelayMs = avgDelayMs;
    }

    public String getPresenceId() {
        return presenceId;
    }

    public String getQualityLabel() {
        return qualityLabel;
    }

    public long getWatchMs() {
        return watchMs;
    }

    public long getTtffMs() {
        return ttffMs;
    }

    public int getStalls() {
        return stalls;
    }

    public int getQualitySwitches() {
        return qualitySwitches;
    }

    public double getPacketLossPct() {
        return packetLossPct;
    }

    public long getRttMs() {
        return rttMs;
    }

    public double getJitterMs() {
        return jitterMs;
    }

    public long getDownloadKbps() {
        return downloadKbps;
    }

    public long getAvgDelayMs() {
        return avgDelayMs;
    }
}
