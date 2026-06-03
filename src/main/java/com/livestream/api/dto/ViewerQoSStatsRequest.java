package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
/** Periodic WebRTC stats from the viewer browser. */
public class ViewerQoSStatsRequest {

    @JsonProperty
    private String presenceId;

    @JsonProperty
    private String qualityLabel;

    @JsonProperty
    private Double packetLossPct;

    @JsonProperty
    private Long rttMs;

    @JsonProperty
    private Double jitterMs;

    @JsonProperty
    private Long downloadKbps;

    public String getPresenceId() {
        return presenceId;
    }

    public void setPresenceId(String presenceId) {
        this.presenceId = presenceId;
    }

    public String getQualityLabel() {
        return qualityLabel;
    }

    public void setQualityLabel(String qualityLabel) {
        this.qualityLabel = qualityLabel;
    }

    public Double getPacketLossPct() {
        return packetLossPct;
    }

    public void setPacketLossPct(Double packetLossPct) {
        this.packetLossPct = packetLossPct;
    }

    public Long getRttMs() {
        return rttMs;
    }

    public void setRttMs(Long rttMs) {
        this.rttMs = rttMs;
    }

    public Double getJitterMs() {
        return jitterMs;
    }

    public void setJitterMs(Double jitterMs) {
        this.jitterMs = jitterMs;
    }

    public Long getDownloadKbps() {
        return downloadKbps;
    }

    public void setDownloadKbps(Long downloadKbps) {
        this.downloadKbps = downloadKbps;
    }
}
