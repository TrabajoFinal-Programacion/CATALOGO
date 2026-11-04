package ar.edu.um.catalogo.catedra.dto;

import java.time.Instant;

/** Profesional tal como lo publica la catedra. */
public record ProfessionalDto(
    Long id,
    Long categoryId,
    String firstName,
    String lastName,
    Boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {}
