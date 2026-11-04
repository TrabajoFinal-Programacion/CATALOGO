package ar.edu.um.catalogo.catedra;

import ar.edu.um.catalogo.catedra.dto.SnapshotDto;
import ar.edu.um.catalogo.config.CatedraProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Cliente REST de la catedra usado por el catalogo (solo el snapshot).
 *
 * - Autentica con el JWT tecnico, que nunca se loguea.
 * - Timeouts de conexion y lectura configurables.
 * - Reintentos acotados solo para fallas transitorias (timeout, conexion, 5xx).
 *   Un 4xx (por ejemplo 401 por token invalido) no se reintenta: repetirlo daria lo mismo.
 * - El snapshot es una lectura, asi que reintentarlo no tiene efectos secundarios.
 */
@Component
public class CatedraClient {

    private static final Logger log = LoggerFactory.getLogger(CatedraClient.class);

    private final RestClient restClient;
    private final CatedraProperties properties;

    public CatedraClient(RestClient.Builder builder, CatedraProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());
        this.restClient = builder
            .baseUrl(properties.apiUrl())
            .requestFactory(requestFactory)
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.token())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    public SnapshotDto fetchSnapshot() {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= properties.maxAttempts(); attempt++) {
            try {
                SnapshotDto snapshot = restClient.get()
                    .uri("/api/synchronization/snapshot")
                    .retrieve()
                    .body(SnapshotDto.class);
                if (snapshot == null) {
                    throw new CatedraUnavailableException("Snapshot vacio", null);
                }
                return snapshot;
            } catch (HttpClientErrorException e) {
                // 4xx: error de configuracion o de contrato. No tiene sentido reintentar.
                throw new CatedraUnavailableException("La catedra rechazo el pedido de snapshot: HTTP "
                    + e.getStatusCode().value(), e);
            } catch (ResourceAccessException | HttpServerErrorException e) {
                last = e;
                log.warn("Snapshot: intento {}/{} fallido ({})", attempt, properties.maxAttempts(),
                    e.getClass().getSimpleName());
                if (attempt < properties.maxAttempts()) {
                    sleep(properties.retryBackoff().toMillis() * attempt);
                }
            }
        }
        throw new CatedraUnavailableException("La catedra no respondio el snapshot luego de "
            + properties.maxAttempts() + " intentos", last);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CatedraUnavailableException("Interrumpido esperando para reintentar", e);
        }
    }
}
