package com.pokego.cooldownengine.controller;

import com.pokego.cooldownengine.model.DestinationSuggestion;
import com.pokego.cooldownengine.service.CooldownService;
import com.pokego.cooldownengine.service.SuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Para cuando conectes el frontend React
public class CooldownController {

    private final CooldownService cooldownService;
    private final SuggestionService suggestionService;

    /**
     * Calcula el cooldown entre dos coordenadas.
     * Ejemplo: GET /api/v1/cooldown/calculate?fromLat=19.4326&fromLon=-99.1332&toLat=35.6595&toLon=139.7004
     */
    @GetMapping("/cooldown/calculate")
    public ResponseEntity<Map<String, Object>> calculate(
            @RequestParam double fromLat,
            @RequestParam double fromLon,
            @RequestParam double toLat,
            @RequestParam double toLon) {

        double distanceKm = cooldownService.calculateDistance(fromLat, fromLon, toLat, toLon);
        int cooldownMin   = cooldownService.getCooldownMinutes(distanceKm);

        log.info("REST /cooldown/calculate — {:.2f} km → {} min",
            distanceKm, cooldownMin);

        return ResponseEntity.ok(Map.of(
            "distanceKm",      Math.round(distanceKm * 100.0) / 100.0,
            "cooldownMinutes", cooldownMin,
            "status",          cooldownMin == 0 ? "SAFE" : "COOLDOWN_REQUIRED",
            "fromCoords",      Map.of("lat", fromLat, "lon", fromLon),
            "toCoords",        Map.of("lat", toLat,   "lon", toLon)
        ));
    }

    /**
     * Devuelve el ranking de mejores destinos desde una coordenada.
     * Ejemplo: GET /api/v1/suggestions?lat=35.6595&lon=139.7004&top=5
     */
    @GetMapping("/suggestions")
    public ResponseEntity<Map<String, Object>> getSuggestions(
            @RequestParam double lat,
            @RequestParam double lon,
            @RequestParam(defaultValue = "5") int top) {

        List<DestinationSuggestion> suggestions =
            suggestionService.getSuggestions(lat, lon, top);

        log.info("REST /suggestions — {} resultados desde ({}, {})", top, lat, lon);

        return ResponseEntity.ok(Map.of(
            "fromCoords",   Map.of("lat", lat, "lon", lon),
            "total",        suggestions.size(),
            "suggestions",  suggestions
        ));
    }

    /**
     * Health check — útil para saber si el servicio está vivo.
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
            "status",  "UP",
            "service", "cooldown-engine",
            "version", "1.0.0"
        ));
    }
}