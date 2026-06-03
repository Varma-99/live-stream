package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class StreamPostmortemResponse {

    @JsonProperty
    private long streamId;

    @JsonProperty
    private long durationMs;

    @JsonProperty
    private boolean zombieStop;

    @JsonProperty
    private StreamQoSResponse summary;

    @JsonProperty
    private String rootCause;

    @JsonProperty
    private List<QoSEventDto> timeline;

    public StreamPostmortemResponse() {}

    public StreamPostmortemResponse(
            long streamId,
            long durationMs,
            boolean zombieStop,
            StreamQoSResponse summary,
            String rootCause,
            List<QoSEventDto> timeline) {
        this.streamId = streamId;
        this.durationMs = durationMs;
        this.zombieStop = zombieStop;
        this.summary = summary;
        this.rootCause = rootCause;
        this.timeline = timeline;
    }

    public static StreamPostmortemResponse empty(long streamId) {
        return new StreamPostmortemResponse(
                streamId, 0, false, StreamQoSResponse.empty(streamId), "none", List.of());
    }

    public long getDurationMs() {
        return durationMs;
    }

    public boolean isZombieStop() {
        return zombieStop;
    }

    public StreamQoSResponse getSummary() {
        return summary;
    }

    public String getRootCause() {
        return rootCause;
    }

    public List<QoSEventDto> getTimeline() {
        return timeline;
    }
}
