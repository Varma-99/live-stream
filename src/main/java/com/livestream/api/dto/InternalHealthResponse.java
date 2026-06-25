package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class InternalHealthResponse {

    private String instanceId;
    private boolean healthy;

    public InternalHealthResponse() {}

    public InternalHealthResponse(String instanceId, boolean healthy) {
        this.instanceId = instanceId;
        this.healthy = healthy;
    }

    @JsonProperty
    public String getInstanceId() {
        return instanceId;
    }

    @JsonProperty
    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }

    @JsonProperty
    public boolean isHealthy() {
        return healthy;
    }

    @JsonProperty
    public void setHealthy(boolean healthy) {
        this.healthy = healthy;
    }
}
