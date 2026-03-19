package com.pokego.cooldownengine.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pokego.cooldownengine.model.CooldownResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class CooldownResultProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${kafka.topics.cooldown-results}")
    private String cooldownResultsTopic;

    /**
     * Publica el resultado del cálculo de cooldown en el topic cooldown-results.
     * Usamos el playerId como key → mismo jugador, misma partition → orden garantizado.
     */
    public void publish(CooldownResult result) {
        try {
            String payload = objectMapper.writeValueAsString(result);

            CompletableFuture<SendResult<String, String>> future =
                kafkaTemplate.send(cooldownResultsTopic, result.getPlayerId(), payload);

            future.whenComplete((sendResult, ex) -> {
                if (ex != null) {
                    log.error("❌ Error publicando en {}: {}", cooldownResultsTopic, ex.getMessage());
                } else {
                    log.debug("📤 Resultado publicado → topic: {}, partition: {}, offset: {}",
                        sendResult.getRecordMetadata().topic(),
                        sendResult.getRecordMetadata().partition(),
                        sendResult.getRecordMetadata().offset()
                    );
                }
            });

        } catch (Exception e) {
            log.error("❌ Error serializando CooldownResult: {}", e.getMessage(), e);
        }
    }
}