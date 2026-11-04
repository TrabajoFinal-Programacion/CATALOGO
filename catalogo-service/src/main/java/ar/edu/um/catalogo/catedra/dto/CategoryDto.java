package ar.edu.um.catalogo.catedra.dto;

import java.time.Instant;

/** Categoria tal como la publica la catedra (snapshot REST y Hash Redis). */
public record CategoryDto(
    Long id,
    String name,
    String description,
    Boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {}
