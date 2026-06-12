package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class StreamQoSResponse {

    @JsonProperty
    private long streamId;

    @JsonProperty
    private int overallHealthScore;

    @JsonProperty
    private IngestQoSDto ingest;

    @JsonProperty
    private EncoderQoSDto encode;

    @JsonProperty
    private ViewerDeliveryQoSDto delivery;

    @JsonProperty
    private ViewerQoEAggregateDto viewer;

    @JsonProperty
    private OpsQoSDto ops;

    @JsonProperty
    private List<QoSEventDto> recentEvents;

    @JsonProperty
    private List<ViewerSessionQoSDto> viewerSessions;

    public StreamQoSResponse() {}

    public StreamQoSResponse(
            long streamId,
            int overallHealthScore,
            IngestQoSDto ingest,
            EncoderQoSDto encode,
            ViewerDeliveryQoSDto delivery,
            ViewerQoEAggregateDto viewer,
            OpsQoSDto ops,
            List<QoSEventDto> recentEvents,
            List<ViewerSessionQoSDto> viewerSessions) {
        this.streamId = streamId;
        this.overallHealthScore = overallHealthScore;
        this.ingest = ingest;
        this.encode = encode;
        this.delivery = delivery;
        this.viewer = viewer;
        this.ops = ops;
        this.recentEvents = recentEvents;
        this.viewerSessions = viewerSessions;
    }

    public int getOverallHealthScore() {
        return overallHealthScore;
    }

    public static StreamQoSResponse empty(long streamId) {
        return new StreamQoSResponse(
                streamId,
                0,
                new IngestQoSDto(0, 0, 0, 0, 0, 0, 0, 0, false, 0),
                new EncoderQoSDto(0, 30, 1.0, 0, 0, 0, 0, 100),
                new ViewerDeliveryQoSDto(0, 0, 0, 0, 0, "unknown"),
                new ViewerQoEAggregateDto(100, 0, 0, 0, 0, 0, 100),
                new OpsQoSDto(0, 0, List.of()),
                List.of(),
                List.of());
    }
}
