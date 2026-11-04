package ar.edu.um.catalogo.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConfig.class);

    /**
     * Reintentos acotados de un mensaje cuyo procesamiento fallo (por ejemplo, Redis caido).
     * Agotados los reintentos se confirma el offset y se sigue: no se bloquea el topic para siempre.
     * No se pierde nada, porque la reconciliacion periodica compara versiones y recupera el atraso.
     * Spring Boot usa automaticamente este bean en el container de los @KafkaListener.
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(SyncProperties properties) {
        FixedBackOff backOff = new FixedBackOff(properties.kafkaRetryInterval().toMillis(), properties.kafkaMaxRetries());
        return new DefaultErrorHandler((record, exception) -> log.warn(
            "Mensaje Kafka descartado tras {} reintentos (topic={}, offset={}): {}. La reconciliacion periodica recuperara el atraso.",
            properties.kafkaMaxRetries(), record.topic(), record.offset(), exception.getMessage()), backOff);
    }
}
