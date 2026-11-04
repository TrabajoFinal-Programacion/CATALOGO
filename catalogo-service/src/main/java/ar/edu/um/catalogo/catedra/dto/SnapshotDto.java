package ar.edu.um.catalogo.catedra.dto;

import java.time.Instant;
import java.util.List;

/** Respuesta de GET /api/synchronization/snapshot. */
public record SnapshotDto(
    Long snapshotVersion,
    Instant generatedAt,
    List<CategoryDto> professionalCategories,
    List<ProfessionalDto> professionals,
    List<WeeklyScheduleDto> weeklySchedules
) {
    public SnapshotDto {
        professionalCategories = professionalCategories == null ? List.of() : professionalCategories;
        professionals = professionals == null ? List.of() : professionals;
        weeklySchedules = weeklySchedules == null ? List.of() : weeklySchedules;
    }
}
