package com.pokego.cooldownengine.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Location {
    private String id;
    private double lat;
    private double lon;
    private String name;
    private String country;
}