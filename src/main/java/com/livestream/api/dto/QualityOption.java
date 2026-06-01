package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** One WebRTC quality rung (fallback ABR via separate WHEP URLs). */
public class QualityOption {

    @JsonProperty
    private String id;

    @JsonProperty
    private String label;

    @JsonProperty
    private String webrtcWhepUrl;

    /** Suggested minimum download speed (kbps) for auto-select. */
    @JsonProperty
    private int minDownloadKbps;

    public QualityOption() {
    }

    public QualityOption(String id, String label, String webrtcWhepUrl, int minDownloadKbps) {
        this.id = id;
        this.label = label;
        this.webrtcWhepUrl = webrtcWhepUrl;
        this.minDownloadKbps = minDownloadKbps;
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public String getWebrtcWhepUrl() {
        return webrtcWhepUrl;
    }

    public int getMinDownloadKbps() {
        return minDownloadKbps;
    }
}
