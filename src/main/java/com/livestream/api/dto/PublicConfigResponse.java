package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Client-facing URLs and stream delivery mode for viewers. */
public class PublicConfigResponse {

    @JsonProperty
    private String publicWebBase;

    /** {@code hls}, {@code mediamtx}, or {@code srs} — selects WHEP proxy upstream. */
    @JsonProperty
    private String streamDelivery;

    @JsonProperty
    private String mediamtxWebrtcBase;

    /** Dev hint for which media-server script matches {@link #streamDelivery}. */
    @JsonProperty
    private String mediaServerStartScript;

    public PublicConfigResponse() {
    }

    public PublicConfigResponse(
            String publicWebBase,
            String streamDelivery,
            String mediamtxWebrtcBase,
            String mediaServerStartScript) {
        this.publicWebBase = publicWebBase;
        this.streamDelivery = streamDelivery;
        this.mediamtxWebrtcBase = mediamtxWebrtcBase;
        this.mediaServerStartScript = mediaServerStartScript;
    }

    public String getPublicWebBase() {
        return publicWebBase;
    }

    public String getStreamDelivery() {
        return streamDelivery;
    }

    public String getMediamtxWebrtcBase() {
        return mediamtxWebrtcBase;
    }

    public String getMediaServerStartScript() {
        return mediaServerStartScript;
    }
}
