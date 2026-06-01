package com.livestream.realtime;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ActiveCoupon {

    @JsonProperty
    private String code;

    @JsonProperty
    private int percentOff;

    @JsonProperty
    private long expiresAt;

    public ActiveCoupon() {
    }

    public ActiveCoupon(String code, int percentOff, long expiresAtEpochMs) {
        this.code = code;
        this.percentOff = percentOff;
        this.expiresAt = expiresAtEpochMs;
    }

    public String getCode() {
        return code;
    }

    public int getPercentOff() {
        return percentOff;
    }

    public long getExpiresAt() {
        return expiresAt;
    }

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAt;
    }
}
