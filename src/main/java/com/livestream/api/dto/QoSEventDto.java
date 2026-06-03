package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class QoSEventDto {

    @JsonProperty
    private long atEpochMs;

    @JsonProperty
    private String stage;

    @JsonProperty
    private String type;

    @JsonProperty
    private String message;

    @JsonProperty
    private String detail;

    public QoSEventDto() {}

    public QoSEventDto(long atEpochMs, String stage, String type, String message, String detail) {
        this.atEpochMs = atEpochMs;
        this.stage = stage;
        this.type = type;
        this.message = message;
        this.detail = detail;
    }
}
