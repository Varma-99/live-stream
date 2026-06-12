package com.livestream.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JoinResponse {

    @JsonProperty
    private String presenceId;

    @JsonProperty
    private RoomSnapshot room;

    public JoinResponse() {
    }

    public JoinResponse(String presenceId, RoomSnapshot room) {
        this.presenceId = presenceId;
        this.room = room;
    }

    public String getPresenceId() {
        return presenceId;
    }

    public RoomSnapshot getRoom() {
        return room;
    }
}
