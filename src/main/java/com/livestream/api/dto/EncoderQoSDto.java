package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class EncoderQoSDto {

    @JsonProperty
    private double fpsActual;

    @JsonProperty
    private int targetFps;

    @JsonProperty
    private double encodeSpeedRatio;

    @JsonProperty
    private long droppedFrames;

    @JsonProperty
    private long duplicatedFrames;

    @JsonProperty
    private int encodeRestartCount;

    @JsonProperty
    private int encodeErrorCount;

    @JsonProperty
    private int encodeHealthScore;

    public EncoderQoSDto() {}

    public EncoderQoSDto(
            double fpsActual,
            int targetFps,
            double encodeSpeedRatio,
            long droppedFrames,
            long duplicatedFrames,
            int encodeRestartCount,
            int encodeErrorCount,
            int encodeHealthScore) {
        this.fpsActual = fpsActual;
        this.targetFps = targetFps;
        this.encodeSpeedRatio = encodeSpeedRatio;
        this.droppedFrames = droppedFrames;
        this.duplicatedFrames = duplicatedFrames;
        this.encodeRestartCount = encodeRestartCount;
        this.encodeErrorCount = encodeErrorCount;
        this.encodeHealthScore = encodeHealthScore;
    }
}
