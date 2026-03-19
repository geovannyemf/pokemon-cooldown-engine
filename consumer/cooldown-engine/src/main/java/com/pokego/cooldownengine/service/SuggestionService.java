package com.pokego.cooldownengine.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokego.cooldownengine.model.DestinationSuggestion;
import com.pokego.cooldownengine.model.DestinationsConfig;
import com.pokego.cooldownengine.model.DestinationsConfig.DestinationData;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionService {

    private final CooldownService cooldownService;
    private final ObjectMapper objectMapper;

    private List<DestinationData> destinations;

    // Umbral: destinos con cooldown <= este valor se consideran "accesibles ahora"
    private static final int COOLDOWN_THRESHOLD_MINUTES = 5;

    @PostConstruct
    public void loadDestinations() {
        try {
            InputStream is = new ClassPathResource("destinations.json").getInputStream();
            DestinationsConfig config = objectMapper.readValue(is, DestinationsConfig.class);
            this.destinations = config.getDestinations();
            log.info("✅ {} destinos cargados desde destinations.json", destinations.size());
        } catch (Exception e) {
            log.error("❌ Error cargando destinations.json: {}", e.getMessage(), e);
        }
    }

    /**
     * Genera un ranking de los mejores destinos desde una ubicación origen.
     *
     * Algoritmo de scoring:
     *   - Densidad de spawns (peso 40%)
     *   - Densidad de pokestops (peso 30%)
     *   - Penalización por cooldown (peso 30%) — menos cooldown = mejor score
     *
     * El objetivo: balancear "a dónde hay más Pokémon" vs "a dónde puedo ir más rápido".
     */
    public List<DestinationSuggestion> getSuggestions(double fromLat, double fromLon, int topN) {
        return destinations.stream()
            .map(dest -> buildSuggestion(dest, fromLat, fromLon))
            .sorted(Comparator.comparingDouble(DestinationSuggestion::getScore).reversed())
            .limit(topN)
            .collect(Collectors.toList());
    }

    private DestinationSuggestion buildSuggestion(DestinationData dest,
                                                   double fromLat, double fromLon) {
        double distanceKm = cooldownService.calculateDistance(fromLat, fromLon,
                                                               dest.getLat(), dest.getLon());
        int cooldownMin = cooldownService.getCooldownMinutes(distanceKm);

        // Score compuesto (0-10)
        double spawnScore     = dest.getSpawns_density() * 0.40;
        double pokestopScore  = dest.getPokestops_density() * 0.30;
        // Cooldown penalty: 120 min = 0 puntos, 0 min = 3 puntos
        double cooldownScore  = (1.0 - (cooldownMin / 122.0)) * 3.0;
        double totalScore     = spawnScore + pokestopScore + cooldownScore;

        String recommendation = totalScore >= 7.5 ? "EXCELENTE"
                              : totalScore >= 6.0 ? "BUENO"
                              : "ACEPTABLE";

        return DestinationSuggestion.builder()
            .destinationId(dest.getId())
            .destinationName(dest.getName())
            .country(dest.getCountry())
            .distanceKm(Math.round(distanceKm * 100.0) / 100.0)
            .cooldownMinutes(cooldownMin)
            .spawnsDensity(dest.getSpawns_density())
            .pokestopsDensity(dest.getPokestops_density())
            .score(Math.round(totalScore * 100.0) / 100.0)
            .recommendation(recommendation)
            .build();
    }
}