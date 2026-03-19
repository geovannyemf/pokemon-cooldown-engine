package com.pokego.cooldownengine.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TeleportEvent {

    @JsonProperty("event_id")
    private String eventId;

    @JsonProperty("player_id")
    private String playerId;

    private String timestamp;

    @JsonProperty("action_type")
    private String actionType;

    @JsonProperty("from_location")
    private Location fromLocation;

    @JsonProperty("to_location")
    private Location toLocation;
}