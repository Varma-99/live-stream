package com.livestream.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "live_streams")
public class LiveStream {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "broadcaster_id", nullable = false)
    private User broadcaster;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(name = "stream_key", nullable = false, unique = true, length = 64)
    private String streamKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private StreamStatus status = StreamStatus.IDLE;

    @Column(name = "view_count", nullable = false)
    private long viewCount;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    /** Per-stream delivery: {@code hls}, {@code webrtc}, or null (use global config default). */
    @Column(length = 16)
    private String delivery;

    /** Test-pattern FFmpeg stream — independent of main camera; no broadcaster zombie stop. */
    @Column(name = "dummy_stream", nullable = false)
    private boolean dummyStream;

    /** Remote FFmpeg RTMP — control plane only; no encoder process on this host. */
    @Column(name = "external_encoder", nullable = false)
    private boolean externalEncoder;

    public LiveStream() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getBroadcaster() {
        return broadcaster;
    }

    public void setBroadcaster(User broadcaster) {
        this.broadcaster = broadcaster;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStreamKey() {
        return streamKey;
    }

    public void setStreamKey(String streamKey) {
        this.streamKey = streamKey;
    }

    public StreamStatus getStatus() {
        return status;
    }

    public void setStatus(StreamStatus status) {
        this.status = status;
    }

    public long getViewCount() {
        return viewCount;
    }

    public void setViewCount(long viewCount) {
        this.viewCount = viewCount;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(Instant endedAt) {
        this.endedAt = endedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getDelivery() {
        return delivery;
    }

    public void setDelivery(String delivery) {
        this.delivery = delivery;
    }

    public boolean isDummyStream() {
        return dummyStream;
    }

    public void setDummyStream(boolean dummyStream) {
        this.dummyStream = dummyStream;
    }

    public boolean isExternalEncoder() {
        return externalEncoder;
    }

    public void setExternalEncoder(boolean externalEncoder) {
        this.externalEncoder = externalEncoder;
    }
}
