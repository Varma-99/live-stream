package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class OpsQoSDto {

    @JsonProperty
    private int activeStreams;

    @JsonProperty
    private int concurrentViewers;

    @JsonProperty
    private List<Long> activeStreamIds;

    @JsonProperty
    private long mttdMs;

    @JsonProperty
    private long mttrMs;

    @JsonProperty
    private long deliveryMttdMs;

    @JsonProperty
    private long deliveryMttrMs;

    @JsonProperty
    private boolean zombieStop;

    public OpsQoSDto() {}

    public OpsQoSDto(int activeStreams, int concurrentViewers, List<Long> activeStreamIds) {
        this.activeStreams = activeStreams;
        this.concurrentViewers = concurrentViewers;
        this.activeStreamIds = activeStreamIds;
        this.mttdMs = -1;
        this.mttrMs = -1;
        this.deliveryMttdMs = -1;
        this.deliveryMttrMs = -1;
        this.zombieStop = false;
    }

    public void setMttdMs(long mttdMs) {
        this.mttdMs = mttdMs;
    }

    public void setMttrMs(long mttrMs) {
        this.mttrMs = mttrMs;
    }

    public void setDeliveryMttdMs(long deliveryMttdMs) {
        this.deliveryMttdMs = deliveryMttdMs;
    }

    public void setDeliveryMttrMs(long deliveryMttrMs) {
        this.deliveryMttrMs = deliveryMttrMs;
    }

    public void setZombieStop(boolean zombieStop) {
        this.zombieStop = zombieStop;
    }
}
