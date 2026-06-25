package com.livestream.config;

import com.fasterxml.jackson.annotation.JsonProperty;

/** Peer app instance for cross-instance stop (Phase 7+). */
public class PeerInstance {

    private String id = "";
    private String host = "";

    @JsonProperty
    public String getId() {
        return id;
    }

    @JsonProperty
    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty
    public String getHost() {
        return host;
    }

    @JsonProperty
    public void setHost(String host) {
        this.host = host;
    }
}
