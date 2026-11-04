package ar.edu.um.catalogo.catedra.dto;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;

/**
 * Horario semanal tal como lo publica la catedra.
 * dayOfWeek usa los mismos nombres que java.time.DayOfWeek (MONDAY..SUNDAY).
 * LocalTime acepta "HH:mm" y "HH:mm:ss".
 */
public record WeeklyScheduleDto(
    Long id,
    Long professionalId,
    DayOfWeek dayOfWeek,
    LocalTime startTime,
    LocalTime endTime,
    Integer slotDurationMinutes,
    Boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {}
