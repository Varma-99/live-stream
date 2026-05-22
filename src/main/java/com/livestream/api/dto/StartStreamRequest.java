package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class StartStreamRequest {

    @NotNull
    @JsonProperty
    private Long broadcasterId;

    @NotBlank
    @Size(max = 255)
    @JsonProperty
    private String title;

    public Long getBroadcasterId() {
        return broadcasterId;
    }

    public void setBroadcasterId(Long broadcasterId) {
        this.broadcasterId = broadcasterId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
