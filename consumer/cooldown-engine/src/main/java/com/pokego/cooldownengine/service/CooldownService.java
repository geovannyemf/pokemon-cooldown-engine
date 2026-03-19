package com.pokego.cooldownengine.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.pokego.cooldownengine.model.CooldownResult;
import com.pokego.cooldownengine.model.TeleportEvent;

import java.time.Instant;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service
public class CooldownService {

    // ─────────────────────────────────────────────────────
    // TABLA DE COOLDOWN OFICIAL (fuente: PGSharp)
    // TreeMap: clave = distancia mínima en km, valor = minutos
    // Usamos TreeMap porque necesitamos buscar el rango
    // más cercano por debajo (floorKey)
    // ─────────────────────────────────────────────────────
    private static final TreeMap<Double, Integer> COOLDOWN_TABLE = new TreeMap<>();

    static {
        COOLDOWN_TABLE.put(0.0,    0);
        COOLDOWN_TABLE.put(1.0,    1);
        COOLDOWN_TABLE.put(2.0,    1);
        COOLDOWN_TABLE.put(4.0,    2);
        COOLDOWN_TABLE.put(10.0,   8);
        COOLDOWN_TABLE.put(15.0,  11);
        COOLDOWN_TABLE.put(25.0,  15);
        COOLDOWN_TABLE.put(30.0,  18);
        COOLDOWN_TABLE.put(40.0,  22);
        COOLDOWN_TABLE.put(45.0,  23);
        COOLDOWN_TABLE.put(60.0,  25);
        COOLDOWN_TABLE.put(80.0,  27);
        COOLDOWN_TABLE.put(100.0, 30);
        COOLDOWN_TABLE.put(125.0, 33);
        COOLDOWN_TABLE.put(150.0, 36);
        COOLDOWN_TABLE.put(180.0, 39);
        COOLDOWN_TABLE.put(200.0, 42);
        COOLDOWN_TABLE.put(300.0, 50);
        COOLDOWN_TABLE.put(500.0, 64);
        COOLDOWN_TABLE.put(600.0, 72);
        COOLDOWN_TABLE.put(700.0, 80);
        COOLDOWN_TABLE.put(800.0, 86);
        COOLDOWN_TABLE.put(1000.0, 100);
        COOLDOWN_TABLE.put(1250.0, 118);
        COOLDOWN_TABLE.put(1266.0, 120);
    }

    // Buffer de seguridad recomendado (minutos extra)
    private static final int SAFETY_BUFFER_MINUTES = 2;

    /**
     * Procesa un evento de teleport y calcula el cooldown requerido.
     */
    public CooldownResult process(TeleportEvent event) {
        double distanceKm = calculateDistance(
            event.getFromLocation().getLat(),
            event.getFromLocation().getLon(),
            event.getToLocation().getLat(),
            event.getToLocation().getLon()
        );

        int cooldownMinutes = getCooldownMinutes(distanceKm);
        int totalWithBuffer = cooldownMinutes > 0
            ? cooldownMinutes + SAFETY_BUFFER_MINUTES
            : 0;

        String status = cooldownMinutes == 0 ? "SAFE" : "COOLDOWN_REQUIRED";

        log.info("🗺️  {} → {} | {} km | {} min cooldown (+{} buffer) | {}",
        	    event.getFromLocation().getName(),
        	    event.getToLocation().getName(),
        	    String.format("%.2f", distanceKm),
        	    cooldownMinutes,
        	    SAFETY_BUFFER_MINUTES,
        	    status
        	);

        return CooldownResult.builder()
            .eventId(event.getEventId())
            .playerId(event.getPlayerId())
            .fromLocation(event.getFromLocation().getName())
            .toLocation(event.getToLocation().getName())
            .distanceKm(Math.round(distanceKm * 100.0) / 100.0)
            .cooldownMinutes(totalWithBuffer)
            .processedAt(Instant.now().toString())
            .status(status)
            .build();
    }

    /**
     * Fórmula de Haversine — distancia entre dos coordenadas GPS en km.
     * Es la misma fórmula que usa Pokémon GO internamente.
     */
    public double calculateDistance(double lat1, double lon1,
                                               double lat2, double lon2) {
        final double EARTH_RADIUS_KM = 6371.0;

        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                 + Math.cos(Math.toRadians(lat1))
                 * Math.cos(Math.toRadians(lat2))
                 * Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }

    /**
     * Busca el cooldown correspondiente a una distancia dada.
     * floorKey devuelve la clave más grande que sea <= distanceKm.
     */
    public int getCooldownMinutes(double distanceKm) {
        if (distanceKm >= 1266.0) return 120;
        Double key = COOLDOWN_TABLE.floorKey(distanceKm);
        if (key == null) return 0;
        return COOLDOWN_TABLE.get(key);
    }
}