package com.pokego.cooldownengine.model;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CooldownResult {
    private String eventId;
    private String playerId;
    private String fromLocation;
    private String toLocation;
    private double distanceKm;
    private int cooldownMinutes;
    private String processedAt;
    private String status; // "SAFE" o "COOLDOWN_REQUIRED"
    private List<DestinationSuggestion> suggestions;
}