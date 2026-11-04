package ar.edu.um.catalogo.sync;

import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lectura y actualizacion del estado observable de la sincronizacion (fila unica sync_state). */
@Service
public class SyncStateService {

    private static final int MAX_ERROR_LENGTH = 1000;

    private final SyncStateRepository repository;
    private final Clock clock;

    public SyncStateService(SyncStateRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public SyncState current() {
        return repository.findById(SyncState.SINGLETON_ID)
            .orElseThrow(() -> new IllegalStateException("Falta la fila de sync_state (migracion V1)"));
    }

    @Transactional(readOnly = true)
    public Long localVersion() {
        return current().getLocalVersion();
    }

    @Transactional
    public void markSyncing(Long remoteVersion) {
        SyncState s = lock();
        s.setStatus(SyncStatus.SYNCING);
        if (remoteVersion != null) {
            s.setRemoteVersion(remoteVersion);
        }
        s.setUpdatedAt(clock.instant());
    }

    @Transactional
    public void markUpToDate(Long remoteVersion) {
        SyncState s = lock();
        s.setStatus(s.getLocalVersion() == null ? SyncStatus.EMPTY : SyncStatus.UP_TO_DATE);
        if (remoteVersion != null) {
            s.setRemoteVersion(remoteVersion);
        }
        s.setLastError(null);
        s.setLastSyncAt(clock.instant());
        s.setUpdatedAt(clock.instant());
    }

    /**
     * Registra el error. La version local NO cambia: los datos siguen siendo los de esa version,
     * que quedaron aplicados completos en una transaccion anterior.
     */
    @Transactional
    public void markError(String message) {
        SyncState s = lock();
        s.setStatus(SyncStatus.ERROR);
        s.setLastError(truncate(message));
        s.setUpdatedAt(clock.instant());
    }

    private SyncState lock() {
        return repository.findForUpdate()
            .orElseThrow(() -> new IllegalStateException("Falta la fila de sync_state (migracion V1)"));
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
