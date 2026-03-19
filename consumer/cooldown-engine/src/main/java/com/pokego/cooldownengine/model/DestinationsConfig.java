package com.pokego.cooldownengine.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
public class DestinationsConfig {
    private List<DestinationData> destinations;

    @Data
    @NoArgsConstructor
    public static class DestinationData {
        private String id;
        private String name;
        private String country;
        private double lat;
        private double lon;

        // snake_case del JSON → camelCase Java
        private double pokestops_density;
        private double spawns_density;
        private String notes;
    }
}