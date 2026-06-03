package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ViewerQoEAggregateDto {

    @JsonProperty
    private double joinSuccessRate;

    @JsonProperty
    private long timeToPlayMs;

    @JsonProperty
    private long totalWatchMs;

    @JsonProperty
    private double stallEventsPerViewerHour;

    @JsonProperty
    private int qualitySwitchCount;

    @JsonProperty
    private int fatalPlaybackErrors;

    @JsonProperty
    private int viewerHealthScore;

    public ViewerQoEAggregateDto() {}

    public ViewerQoEAggregateDto(
            double joinSuccessRate,
            long timeToPlayMs,
            long totalWatchMs,
            double stallEventsPerViewerHour,
            int qualitySwitchCount,
            int fatalPlaybackErrors,
            int viewerHealthScore) {
        this.joinSuccessRate = joinSuccessRate;
        this.timeToPlayMs = timeToPlayMs;
        this.totalWatchMs = totalWatchMs;
        this.stallEventsPerViewerHour = stallEventsPerViewerHour;
        this.qualitySwitchCount = qualitySwitchCount;
        this.fatalPlaybackErrors = fatalPlaybackErrors;
        this.viewerHealthScore = viewerHealthScore;
    }
}
