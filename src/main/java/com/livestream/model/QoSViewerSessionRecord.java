package com.livestream.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "qos_viewer_sessions")
public class QoSViewerSessionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private QoSSessionRecord session;

    @Column(name = "presence_id", length = 64)
    private String presenceId;

    @Column(name = "quality_label", length = 16)
    private String qualityLabel;

    @Column(name = "watch_ms")
    private Long watchMs;

    @Column(name = "ttff_ms")
    private Long ttffMs;

    @Column
    private Integer stalls;

    @Column(name = "quality_switches")
    private Integer qualitySwitches;

    @Column(name = "packet_loss_pct")
    private Double packetLossPct;

    @Column(name = "rtt_ms")
    private Long rttMs;

    @Column(name = "jitter_ms")
    private Double jitterMs;

    @Column(name = "download_kbps")
    private Long downloadKbps;

    @Column(name = "avg_delay_ms")
    private Long avgDelayMs;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public QoSSessionRecord getSession() {
        return session;
    }

    public void setSession(QoSSessionRecord session) {
        this.session = session;
    }

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

    public Long getWatchMs() {
        return watchMs;
    }

    public void setWatchMs(Long watchMs) {
        this.watchMs = watchMs;
    }

    public Long getTtffMs() {
        return ttffMs;
    }

    public void setTtffMs(Long ttffMs) {
        this.ttffMs = ttffMs;
    }

    public Integer getStalls() {
        return stalls;
    }

    public void setStalls(Integer stalls) {
        this.stalls = stalls;
    }

    public Integer getQualitySwitches() {
        return qualitySwitches;
    }

    public void setQualitySwitches(Integer qualitySwitches) {
        this.qualitySwitches = qualitySwitches;
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

    public Long getAvgDelayMs() {
        return avgDelayMs;
    }

    public void setAvgDelayMs(Long avgDelayMs) {
        this.avgDelayMs = avgDelayMs;
    }
}
