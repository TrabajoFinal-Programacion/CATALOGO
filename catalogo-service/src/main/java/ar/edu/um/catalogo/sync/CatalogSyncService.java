package ar.edu.um.catalogo.sync;

import ar.edu.um.catalogo.catedra.CatedraClient;
import ar.edu.um.catalogo.catedra.dto.ChangeSetDto;
import ar.edu.um.catalogo.catedra.dto.SnapshotDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Coordina la sincronizacion del catalogo.
 *
 * Se invoca desde tres lugares: el consumidor Kafka (CatalogUpdated), la reconciliacion periodica
 * y el endpoint administrativo. El metodo es synchronized: nunca hay dos sincronizaciones en paralelo
 * dentro del servicio, y cada transaccion ademas bloquea la fila de estado (SELECT FOR UPDATE).
 *
 * Kafka solo notifica. La fuente es Redis (incremental) o el snapshot REST (completa), y la decision
 * siempre se toma comparando versiones, por eso una notificacion perdida o repetida no rompe nada.
 */
@Service
public class CatalogSyncService {

    private static final Logger log = LoggerFactory.getLogger(CatalogSyncService.class);

    private final CatalogRedisReader redisReader;
    private final CatedraClient catedraClient;
    private final CatalogApplier applier;
    private final SyncStateService stateService;
    private final SyncDiagnostics diagnostics;

    public CatalogSyncService(CatalogRedisReader redisReader, CatedraClient catedraClient, CatalogApplier applier,
                              SyncStateService stateService, SyncDiagnostics diagnostics) {
        this.redisReader = redisReader;
        this.catedraClient = catedraClient;
        this.applier = applier;
        this.stateService = stateService;
        this.diagnostics = diagnostics;
    }

    /**
     * Lleva la copia local a la ultima version publicada.
     *
     * @throws SyncFailedException si no se pudo (Redis o la catedra no disponibles, datos invalidos
     *         en el snapshot). La copia local queda intacta en su ultima version completa.
     */
    public synchronized SyncOutcome synchronize() {
        Long before = stateService.localVersion();
        try {
            RemoteVersions remote = redisReader.readVersions();
            SyncPlan plan = SyncPlanner.plan(before, remote);
            SyncOutcome outcome = switch (plan.type()) {
                case NONE -> new SyncOutcome(SyncPlan.Type.NONE, before, before, plan.reason());
                case FULL -> fullSync(before, plan.reason(), remote.current());
                case INCREMENTAL -> incrementalOrFull(before, plan, remote.current());
            };
            stateService.markUpToDate(remote.current());
            finish(outcome);
            return outcome;
        } catch (CatalogInconsistencyException e) {
            // Redis sin metadata o con datos invalidos: el snapshot REST es la fuente alternativa.
            log.warn("No se puede verificar el estado publicado en Redis ({}); se usa el snapshot", e.getMessage());
            return fullSyncOrFail(before, "REDIS_INCONSISTENTE");
        } catch (SyncFailedException e) {
            throw e;
        } catch (RuntimeException e) {
            // Redis caido, timeout, etc.: no se cambia nada y se reintenta mas tarde.
            throw fail("Error de infraestructura durante la sincronizacion", e);
        }
    }

    /** Fuerza una sincronizacion completa (operacion administrativa o reconstruccion manual). */
    public synchronized SyncOutcome forceFullSync() {
        Long before = stateService.localVersion();
        return fullSyncOrFail(before, "FORZADA");
    }

    private SyncOutcome incrementalOrFull(Long before, SyncPlan plan, long remoteCurrent) {
        stateService.markSyncing(remoteCurrent);
        long applied = before;
        try {
            for (long v = plan.fromVersion(); v <= plan.toVersion(); v++) {
                ChangeSetDto changes = redisReader.readChanges(v);
                boolean ok = applier.applyVersion(v, applied,
                    redisReader.readCategories(changes.changes().professionalCategories()),
                    redisReader.readProfessionals(changes.changes().professionals()),
                    redisReader.readSchedules(changes.changes().weeklySchedules()));
                if (!ok) {
                    throw new CatalogInconsistencyException("La version local cambio durante la sincronizacion");
                }
                applied = v;
                log.info("Catalogo: version {} aplicada", v);
            }
            return new SyncOutcome(SyncPlan.Type.INCREMENTAL, before, applied, plan.reason());
        } catch (CatalogInconsistencyException e) {
            // Discontinuidad: falta changes:{v}, falta una entidad o un dato es invalido.
            // Las versiones ya aplicadas quedan; el snapshot reconstruye desde ahi.
            log.warn("Sincronizacion incremental interrumpida en version {}: {}", applied + 1, e.getMessage());
            return fullSync(before, "DISCONTINUIDAD", remoteCurrent);
        }
    }

    private SyncOutcome fullSyncOrFail(Long before, String reason) {
        try {
            SyncOutcome outcome = fullSync(before, reason, null);
            stateService.markUpToDate(null);
            finish(outcome);
            return outcome;
        } catch (RuntimeException e) {
            throw fail("No se pudo completar la sincronizacion completa (" + reason + ")", e);
        }
    }

    private SyncOutcome fullSync(Long before, String reason, Long remoteCurrent) {
        stateService.markSyncing(remoteCurrent);
        log.info("Catalogo: sincronizacion completa ({})", reason);
        SnapshotDto snapshot = catedraClient.fetchSnapshot();
        try {
            applier.applySnapshot(snapshot);
        } catch (CatalogInconsistencyException e) {
            // El snapshot es el ultimo recurso: si es invalido no hay a donde volver. Se reporta el error
            // (y no como inconsistencia, para no disparar otro snapshot en cadena).
            throw new IllegalStateException("Snapshot invalido: " + e.getMessage(), e);
        }
        log.info("Catalogo: snapshot version {} aplicado", snapshot.snapshotVersion());
        return new SyncOutcome(SyncPlan.Type.FULL, before, snapshot.snapshotVersion(), reason);
    }

    private void finish(SyncOutcome outcome) {
        if (outcome.type() != SyncPlan.Type.NONE) {
            diagnostics.record(outcome.type().name(), outcome.currentVersion(), outcome.reason());
        }
    }

    private SyncFailedException fail(String message, RuntimeException cause) {
        String detail = message + ": " + cause.getClass().getSimpleName()
            + (cause.getMessage() == null ? "" : " - " + cause.getMessage());
        log.warn(detail);
        try {
            stateService.markError(detail);
        } catch (RuntimeException e) {
            log.warn("Tampoco se pudo registrar el error en sync_state: {}", e.getMessage());
        }
        diagnostics.record("ERROR", safeLocalVersion(), detail);
        return new SyncFailedException(detail, cause);
    }

    private Long safeLocalVersion() {
        try {
            return stateService.localVersion();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
