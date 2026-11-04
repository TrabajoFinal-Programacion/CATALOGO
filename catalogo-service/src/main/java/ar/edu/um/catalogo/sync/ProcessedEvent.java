package ar.edu.um.catalogo.sync;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Evento Kafka ya procesado. eventId es la clave de idempotencia definida por el contrato. */
@Entity
@Table(name = "processed_event")
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", length = 200)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Column(name = "duplicate_count", nullable = false)
    private int duplicateCount;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    protected ProcessedEvent() {}

    public String getEventId() { return eventId; }
    public String getEventType() { return eventType; }
    public Instant getProcessedAt() { return processedAt; }
    public int getDuplicateCount() { return duplicateCount; }
    public Instant getLastSeenAt() { return lastSeenAt; }
}
