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
@Table(name = "qos_events")
public class QoSEventRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private QoSSessionRecord session;

    @Column(name = "at_epoch_ms", nullable = false)
    private long atEpochMs;

    @Column(nullable = false, length = 16)
    private String stage;

    @Column(nullable = false, length = 32)
    private String type;

    @Column
    private String message;

    @Column
    private String detail;

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

    public long getAtEpochMs() {
        return atEpochMs;
    }

    public void setAtEpochMs(long atEpochMs) {
        this.atEpochMs = atEpochMs;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getDetail() {
        return detail;
    }

    public void setDetail(String detail) {
        this.detail = detail;
    }
}
