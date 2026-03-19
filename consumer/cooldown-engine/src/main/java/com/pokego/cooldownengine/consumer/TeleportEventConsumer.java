package com.pokego.cooldownengine.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokego.cooldownengine.model.CooldownResult;
import com.pokego.cooldownengine.model.DestinationSuggestion;
import com.pokego.cooldownengine.model.TeleportEvent;
import com.pokego.cooldownengine.producer.CooldownResultProducer;
import com.pokego.cooldownengine.service.CooldownService;
import com.pokego.cooldownengine.service.SuggestionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TeleportEventConsumer {

    private final CooldownService cooldownService;
    private final SuggestionService suggestionService;
    private final CooldownResultProducer resultProducer;
    private final ObjectMapper objectMapper;

    @KafkaListener(
        topics = "${kafka.topics.teleport-events}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void consume(ConsumerRecord<String, String> record) {
        log.debug("📨 Mensaje recibido — Partition: {}, Offset: {}, Key: {}",
            record.partition(), record.offset(), record.key());

        try {
            TeleportEvent event = objectMapper.readValue(record.value(), TeleportEvent.class);

            // 1. Calcular cooldown del teleport actual
            CooldownResult result = cooldownService.process(event);

            // 2. Generar top 5 sugerencias desde la ubicación destino
            List<DestinationSuggestion> suggestions = suggestionService.getSuggestions(
                event.getToLocation().getLat(),
                event.getToLocation().getLon(),
                5
            );
            result.setSuggestions(suggestions);

            // 3. Loguear ranking
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
            log.info("🎮 TELEPORT: {} → {}",
                result.getFromLocation(), result.getToLocation());
            log.info("📏 Distancia: {} km | ⏱️  Cooldown: {} min | {}",
                result.getDistanceKm(), result.getCooldownMinutes(), result.getStatus());
            log.info("🏆 TOP 5 DESTINOS DESDE {}:", result.getToLocation());
            for (int i = 0; i < suggestions.size(); i++) {
                DestinationSuggestion s = suggestions.get(i);
                log.info("  {}. {} ({}) | {} km | {} min CD | Score: {} | {}",
                    i + 1,
                    s.getDestinationName(),
                    s.getCountry(),
                    s.getDistanceKm(),
                    s.getCooldownMinutes(),
                    s.getScore(),
                    s.getRecommendation()
                );
            }
            log.info("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

            // 4. Publicar resultado en Kafka
            resultProducer.publish(result);

        } catch (Exception e) {
            log.error("❌ Error procesando mensaje offset {}: {}",
                record.offset(), e.getMessage(), e);
        }
    }
}