package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Client-facing URLs for Wi-Fi / LAN viewers (Method A). */
public class PublicConfigResponse {

    @JsonProperty
    private String publicWebBase;

    @JsonProperty
    private String mediamtxWebrtcBase;

    public PublicConfigResponse() {
    }

    public PublicConfigResponse(String publicWebBase, String mediamtxWebrtcBase) {
        this.publicWebBase = publicWebBase;
        this.mediamtxWebrtcBase = mediamtxWebrtcBase;
    }

    public String getPublicWebBase() {
        return publicWebBase;
    }

    public String getMediamtxWebrtcBase() {
        return mediamtxWebrtcBase;
    }
}
