package ar.edu.um.catalogo.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Fila unica (id = 1) con la version aplicada y el estado de la ultima sincronizacion. */
@Entity
@Table(name = "sync_state")
public class SyncState {

    public static final short SINGLETON_ID = 1;

    @Id
    private Short id;

    @Column(name = "local_version")
    private Long localVersion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SyncStatus status;

    @Column(name = "last_sync_at")
    private Instant lastSyncAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Ultima version publicada que se observo en Redis (para informar el atraso). */
    @Column(name = "remote_version")
    private Long remoteVersion;

    @Column(name = "last_full_sync_at")
    private Instant lastFullSyncAt;

    protected SyncState() {}

    public Short getId() { return id; }
    public Long getLocalVersion() { return localVersion; }
    public void setLocalVersion(Long localVersion) { this.localVersion = localVersion; }
    public SyncStatus getStatus() { return status; }
    public void setStatus(SyncStatus status) { this.status = status; }
    public Instant getLastSyncAt() { return lastSyncAt; }
    public void setLastSyncAt(Instant lastSyncAt) { this.lastSyncAt = lastSyncAt; }
    public String getLastError() { return lastError; }
    public void setLastError(String lastError) { this.lastError = lastError; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public Long getRemoteVersion() { return remoteVersion; }
    public void setRemoteVersion(Long remoteVersion) { this.remoteVersion = remoteVersion; }
    public Instant getLastFullSyncAt() { return lastFullSyncAt; }
    public void setLastFullSyncAt(Instant lastFullSyncAt) { this.lastFullSyncAt = lastFullSyncAt; }
}
