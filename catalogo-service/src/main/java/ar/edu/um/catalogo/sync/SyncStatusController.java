package ar.edu.um.catalogo.sync;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estado de la sincronizacion (requisito 4.1: "informar el estado y los errores de sincronizacion")
 * y operaciones administrativas.
 */
@RestController
@RequestMapping("/api/sync")
public class SyncStatusController {

    private final SyncStateService stateService;
    private final CatalogSyncService syncService;

    public SyncStatusController(SyncStateService stateService, CatalogSyncService syncService) {
        this.stateService = stateService;
        this.syncService = syncService;
    }

    /** ROLE_USER. */
    @GetMapping("/status")
    public SyncStatusResponse status() {
        return SyncStatusResponse.from(stateService.current());
    }

    /** ROLE_ADMIN (SecurityConfig). Reconstruye la copia local desde el snapshot. */
    @PostMapping("/full")
    public SyncOutcome forceFull() {
        return syncService.forceFullSync();
    }

    /** ROLE_ADMIN. Ejecuta una reconciliacion inmediata (sin esperar a Kafka ni al scheduler). */
    @PostMapping("/run")
    public SyncOutcome run() {
        return syncService.synchronize();
    }
}
