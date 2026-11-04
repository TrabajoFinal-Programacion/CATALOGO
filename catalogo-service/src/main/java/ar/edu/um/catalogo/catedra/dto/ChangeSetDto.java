package ar.edu.um.catalogo.catedra.dto;

import java.util.List;

/** Contenido de catedra:sync:changes:{version}: IDs afectados (no entidades completas). */
public record ChangeSetDto(Long version, Changes changes) {

    public record Changes(List<Long> professionalCategories, List<Long> professionals, List<Long> weeklySchedules) {
        public Changes {
            professionalCategories = professionalCategories == null ? List.of() : professionalCategories;
            professionals = professionals == null ? List.of() : professionals;
            weeklySchedules = weeklySchedules == null ? List.of() : weeklySchedules;
        }
    }

    public ChangeSetDto {
        changes = changes == null ? new Changes(null, null, null) : changes;
    }
}
