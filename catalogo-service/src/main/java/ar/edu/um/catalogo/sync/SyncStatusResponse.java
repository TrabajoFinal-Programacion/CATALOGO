package ar.edu.um.catalogo.sync;

import java.time.Instant;

/** Estado observable de la sincronizacion. */
public record SyncStatusResponse(
    Long localVersion,
    Long remoteVersion,
    SyncStatus status,
    Instant lastSyncAt,
    Instant lastFullSyncAt,
    String lastError
) {
    public static SyncStatusResponse from(SyncState s) {
        return new SyncStatusResponse(s.getLocalVersion(), s.getRemoteVersion(), s.getStatus(), s.getLastSyncAt(),
            s.getLastFullSyncAt(), s.getLastError());
    }
}
