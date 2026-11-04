package ar.edu.um.catalogo.sync;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Reconciliacion periodica. Cubre lo que Kafka no garantiza:
 * - al arrancar (base vacia o reinicio con cambios pendientes),
 * - notificaciones perdidas (compara la version local con la publicada en Redis),
 * - errores anteriores (reintenta en el proximo ciclo, sin bucles infinitos).
 * Si no hay cambios, el costo es una lectura de un Hash en Redis.
 */
@Component
@ConditionalOnProperty(name = "app.sync.scheduler-enabled", havingValue = "true", matchIfMissing = true)
public class SyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(SyncScheduler.class);

    private final CatalogSyncService syncService;

    public SyncScheduler(CatalogSyncService syncService) {
        this.syncService = syncService;
    }

    @Scheduled(initialDelayString = "${app.sync.initial-delay}", fixedDelayString = "${app.sync.reconcile-interval}")
    public void reconcile() {
        try {
            SyncOutcome outcome = syncService.synchronize();
            if (outcome.type() != SyncPlan.Type.NONE) {
                log.info("Reconciliacion: {} {} -> {}", outcome.type(), outcome.previousVersion(), outcome.currentVersion());
            }
        } catch (SyncFailedException e) {
            // Ya quedo registrado en sync_state; se reintenta en el proximo ciclo.
            log.debug("Reconciliacion fallida: {}", e.getMessage());
        }
    }
}
