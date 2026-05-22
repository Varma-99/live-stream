package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.livestream.model.LiveStream;
import com.livestream.model.StreamStatus;
import java.time.Instant;

public class StreamResponse {

    @JsonProperty
    private Long id;

    @JsonProperty
    private String title;

    @JsonProperty
    private StreamStatus status;

    @JsonProperty
    private String streamKey;

    @JsonProperty
    private long viewCount;

    @JsonProperty
    private Long broadcasterId;

    @JsonProperty
    private String broadcasterName;

    @JsonProperty
    private Instant startedAt;

    @JsonProperty
    private String playbackUrl;

    public static StreamResponse from(LiveStream stream) {
        StreamResponse response = new StreamResponse();
        response.id = stream.getId();
        response.title = stream.getTitle();
        response.status = stream.getStatus();
        response.streamKey = stream.getStreamKey();
        response.viewCount = stream.getViewCount();
        response.broadcasterId = stream.getBroadcaster().getId();
        response.broadcasterName = stream.getBroadcaster().getDisplayName();
        response.startedAt = stream.getStartedAt();
        return response;
    }

    public void setPlaybackUrl(String playbackUrl) {
        this.playbackUrl = playbackUrl;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public StreamStatus getStatus() {
        return status;
    }

    public String getStreamKey() {
        return streamKey;
    }

    public long getViewCount() {
        return viewCount;
    }

    public Long getBroadcasterId() {
        return broadcasterId;
    }

    public String getBroadcasterName() {
        return broadcasterName;
    }

    public Instant getStartedAt() {
        return startedAt;
    }
}
