package ar.edu.um.catalogo.catedra.dto;

import java.time.Instant;

/** Evento CatalogUpdated del topic catedra.catalog.{groupId}. Los campos desconocidos se ignoran. */
public record CatalogUpdatedEvent(
    String eventId,
    String eventType,
    Long newVersion,
    Instant occurredAt,
    String source,
    Integer schemaVersion
) {
    public static final String TYPE = "CatalogUpdated";
}
