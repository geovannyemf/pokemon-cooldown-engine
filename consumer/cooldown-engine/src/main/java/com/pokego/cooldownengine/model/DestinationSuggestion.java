package com.pokego.cooldownengine.model;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DestinationSuggestion {
    private String destinationId;
    private String destinationName;
    private String country;
    private double distanceKm;
    private int cooldownMinutes;      // con buffer incluido
    private double spawnsDensity;
    private double pokestopsDensity;
    private double score;             // score compuesto para el ranking
    private String recommendation;    // "EXCELENTE", "BUENO", "ACEPTABLE"
}