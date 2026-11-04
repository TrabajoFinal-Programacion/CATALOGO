package ar.edu.um.catalogo.sync;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, String> {

    /** Registra el evento. ON CONFLICT DO NOTHING: si dos entregas compiten, no falla ninguna. */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO processed_event (event_id, event_type, processed_at, duplicate_count, last_seen_at)
        VALUES (:eventId, :eventType, :now, 0, :now)
        ON CONFLICT (event_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("eventId") String eventId, @Param("eventType") String eventType, @Param("now") Instant now);

    /** Deja constancia de una entrega repetida (observabilidad de duplicados). */
    @Modifying
    @Transactional
    @Query("update ProcessedEvent e set e.duplicateCount = e.duplicateCount + 1, e.lastSeenAt = :now where e.eventId = :eventId")
    int registerDuplicate(@Param("eventId") String eventId, @Param("now") Instant now);
}
