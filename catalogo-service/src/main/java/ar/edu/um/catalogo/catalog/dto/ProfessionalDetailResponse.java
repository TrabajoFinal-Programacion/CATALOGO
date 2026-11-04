package ar.edu.um.catalogo.catalog.dto;

import java.util.List;

/**
 * Profesional con su agenda semanal. Es lo que consume el servicio de turnos para validar
 * la informacion vigente antes de iniciar una reserva. catalogVersion indica de que version
 * del catalogo salen estos datos.
 */
public record ProfessionalDetailResponse(
    Long id,
    String firstName,
    String lastName,
    boolean enabled,
    Long categoryId,
    CategoryRef category,
    List<ScheduleResponse> schedules,
    long catalogVersion
) {}
