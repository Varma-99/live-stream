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
import java.time.Instant;

@Entity
@Table(name = "qos_sessions")
public class QoSSessionRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stream_id", nullable = false)
    private LiveStream stream;

    @Column(name = "started_at_ms", nullable = false)
    private long startedAtMs;

    @Column(name = "ended_at_ms")
    private Long endedAtMs;

    @Column(name = "zombie_stop", nullable = false)
    private boolean zombieStop;

    @Column(name = "overall_score")
    private Integer overallScore;

    @Column(name = "root_cause", length = 32)
    private String rootCause;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "delivery_mode", length = 16)
    private String deliveryMode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LiveStream getStream() {
        return stream;
    }

    public void setStream(LiveStream stream) {
        this.stream = stream;
    }

    public long getStartedAtMs() {
        return startedAtMs;
    }

    public void setStartedAtMs(long startedAtMs) {
        this.startedAtMs = startedAtMs;
    }

    public Long getEndedAtMs() {
        return endedAtMs;
    }

    public void setEndedAtMs(Long endedAtMs) {
        this.endedAtMs = endedAtMs;
    }

    public boolean isZombieStop() {
        return zombieStop;
    }

    public void setZombieStop(boolean zombieStop) {
        this.zombieStop = zombieStop;
    }

    public Integer getOverallScore() {
        return overallScore;
    }

    public void setOverallScore(Integer overallScore) {
        this.overallScore = overallScore;
    }

    public String getRootCause() {
        return rootCause;
    }

    public void setRootCause(String rootCause) {
        this.rootCause = rootCause;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getDeliveryMode() {
        return deliveryMode;
    }

    public void setDeliveryMode(String deliveryMode) {
        this.deliveryMode = deliveryMode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
