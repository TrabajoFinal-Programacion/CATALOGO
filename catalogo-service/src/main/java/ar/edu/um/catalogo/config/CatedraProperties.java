package ar.edu.um.catalogo.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Integracion con el servicio de la catedra (cuenta tecnica del proyecto).
 * Todos los valores vienen de variables de entorno; si falta alguno obligatorio
 * el servicio no arranca, con un mensaje que indica cual.
 */
@Validated
@ConfigurationProperties(prefix = "app.catedra")
public record CatedraProperties(
    @NotBlank String apiUrl,
    /** JWT tecnico de catedra. Nunca se loguea ni se expone. */
    @NotBlank String token,
    /** groupId normalizado que devolvio la catedra en "integration". */
    @NotBlank String groupId,
    @NotBlank String kafkaCatalogTopic,
    @NotBlank String kafkaConsumerGroup,
    @NotNull Duration connectTimeout,
    @NotNull Duration readTimeout,
    @Min(1) int maxAttempts,
    @NotNull Duration retryBackoff
) {
    @Override
    public String toString() {
        // Evita que el token termine en un log si alguien imprime la configuracion.
        return "CatedraProperties[apiUrl=" + apiUrl + ", groupId=" + groupId + ", token=***]";
    }
}
