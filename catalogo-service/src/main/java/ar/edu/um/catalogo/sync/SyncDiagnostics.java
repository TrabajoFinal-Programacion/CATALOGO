package ar.edu.um.catalogo.sync;

import ar.edu.um.catalogo.config.CatedraProperties;
import ar.edu.um.catalogo.config.SyncProperties;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica el resultado de la ultima sincronizacion en el namespace privado de Redis
 * (alumnos:{groupId}:diagnostics:last-sync), como permite el contrato 14.6.
 * Es solo diagnostico: no es fuente de verdad y su falla nunca afecta la sincronizacion.
 */
@Component
public class SyncDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(SyncDiagnostics.class);
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redis;
    private final SyncProperties syncProperties;
    private final String key;
    private final Clock clock;

    public SyncDiagnostics(StringRedisTemplate redis, SyncProperties syncProperties, CatedraProperties catedra,
                           Clock clock) {
        this.redis = redis;
        this.syncProperties = syncProperties;
        this.key = "alumnos:" + catedra.groupId() + ":diagnostics:last-sync";
        this.clock = clock;
    }

    public void record(String result, Long localVersion, String detail) {
        if (!syncProperties.diagnosticsEnabled()) {
            return;
        }
        try {
            Map<String, String> values = new LinkedHashMap<>();
            values.put("result", result);
            values.put("localVersion", localVersion == null ? "" : localVersion.toString());
            values.put("detail", detail == null ? "" : detail);
            values.put("at", clock.instant().toString());
            redis.opsForHash().putAll(key, values);
            redis.expire(key, TTL);
        } catch (RuntimeException e) {
            log.debug("No se pudo escribir el diagnostico en Redis: {}", e.getMessage());
        }
    }
}
