package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class StartDummyStreamRequest {

    @NotBlank
    @Size(max = 255)
    @JsonProperty
    private String title;

    /** Optional: {@code hls}, {@code webrtc}, or {@code auto} (global config default). */
    @Pattern(regexp = "^(?i)(hls|webrtc|auto)$", message = "delivery must be hls, webrtc, or auto")
    @JsonProperty
    private String delivery;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDelivery() {
        return delivery;
    }

    public void setDelivery(String delivery) {
        this.delivery = delivery;
    }
}
