package ar.edu.um.catalogo.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Parametros de la sincronizacion del catalogo. */
@Validated
@ConfigurationProperties(prefix = "app.sync")
public record SyncProperties(
    /** Reconciliacion periodica: detecta notificaciones Kafka perdidas comparando versiones. */
    boolean schedulerEnabled,
    @NotNull Duration initialDelay,
    @NotNull Duration reconcileInterval,
    /** Reintentos acotados de un mensaje Kafka cuya sincronizacion fallo. */
    @NotNull Duration kafkaRetryInterval,
    @Min(0) int kafkaMaxRetries,
    /** Escribe el resultado de cada sincronizacion en el namespace privado de Redis. */
    boolean diagnosticsEnabled
) {}
