package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.livestream.realtime.ActiveCoupon;

public class RoomSnapshot {

    @JsonProperty
    private int viewers;

    @JsonProperty
    private int likes;

    @JsonProperty
    private ActiveCoupon coupon;

    /** {@code LIVE} or {@code PAUSED} while broadcast is active. */
    @JsonProperty
    private String streamStatus;

    @JsonProperty
    private boolean ingestUnstable;

    @JsonProperty
    private long ingestBitrateKbps;

    @JsonProperty
    private String ingestWarning;

    public RoomSnapshot() {
    }

    public RoomSnapshot(int viewers, int likes, ActiveCoupon coupon) {
        this(viewers, likes, coupon, null, false, 0, null);
    }

    public RoomSnapshot(int viewers, int likes, ActiveCoupon coupon, String streamStatus) {
        this(viewers, likes, coupon, streamStatus, false, 0, null);
    }

    public RoomSnapshot(
            int viewers,
            int likes,
            ActiveCoupon coupon,
            String streamStatus,
            boolean ingestUnstable,
            long ingestBitrateKbps,
            String ingestWarning) {
        this.viewers = viewers;
        this.likes = likes;
        this.coupon = coupon;
        this.streamStatus = streamStatus;
        this.ingestUnstable = ingestUnstable;
        this.ingestBitrateKbps = ingestBitrateKbps;
        this.ingestWarning = ingestWarning;
    }

    public int getViewers() {
        return viewers;
    }

    public int getLikes() {
        return likes;
    }

    public ActiveCoupon getCoupon() {
        return coupon;
    }

    public String getStreamStatus() {
        return streamStatus;
    }

    public boolean isIngestUnstable() {
        return ingestUnstable;
    }

    public long getIngestBitrateKbps() {
        return ingestBitrateKbps;
    }

    public String getIngestWarning() {
        return ingestWarning;
    }
}
