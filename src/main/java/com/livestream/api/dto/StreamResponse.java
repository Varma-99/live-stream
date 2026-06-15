package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.livestream.model.LiveStream;
import com.livestream.model.StreamStatus;
import java.time.Instant;
import java.util.List;

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

    /** {@code hls} or {@code webrtc} */
    @JsonProperty
    private String delivery;

    /** MediaMTX WHEP endpoint for browser WebRTC playback. */
    @JsonProperty
    private String webrtcWhepUrl;

    /** Fallback ABR rungs (high → low); each has its own WHEP URL. */
    @JsonProperty
    private List<QualityOption> qualities;

    /** Dev demo: FFmpeg running with throttled encode (100k / 5fps). */
    @JsonProperty
    private boolean encodeDegraded;

    /** Test-pattern stream — not tied to main camera / broadcaster tab. */
    @JsonProperty
    private boolean dummy;

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
        response.dummy = stream.isDummyStream();
        return response;
    }

    public void setPlaybackUrl(String playbackUrl) {
        this.playbackUrl = playbackUrl;
    }

    public void setDelivery(String delivery) {
        this.delivery = delivery;
    }

    public void setWebrtcWhepUrl(String webrtcWhepUrl) {
        this.webrtcWhepUrl = webrtcWhepUrl;
    }

    public void setQualities(List<QualityOption> qualities) {
        this.qualities = qualities;
    }

    public void setEncodeDegraded(boolean encodeDegraded) {
        this.encodeDegraded = encodeDegraded;
    }

    public List<QualityOption> getQualities() {
        return qualities;
    }

    public boolean isEncodeDegraded() {
        return encodeDegraded;
    }

    public String getDelivery() {
        return delivery;
    }

    public String getWebrtcWhepUrl() {
        return webrtcWhepUrl;
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

    public boolean isDummy() {
        return dummy;
    }
}
