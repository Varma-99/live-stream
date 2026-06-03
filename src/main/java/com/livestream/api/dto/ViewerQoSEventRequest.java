package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class ViewerQoSEventRequest {

    @JsonProperty
    private String presenceId;

    @NotBlank
    @JsonProperty
    private String eventType;

    @JsonProperty
    private String message;

    /** Optional numeric detail (e.g. TTFF ms). */
    @JsonProperty
    private String detail;

    public String getPresenceId() {
        return presenceId;
    }

    public void setPresenceId(String presenceId) {
        this.presenceId = presenceId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
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
