package ar.edu.um.catalogo.sync;

import ar.edu.um.catalogo.catedra.dto.CategoryDto;
import ar.edu.um.catalogo.catedra.dto.ProfessionalDto;
import ar.edu.um.catalogo.catedra.dto.WeeklyScheduleDto;
import java.util.Collection;

/**
 * Validacion de los datos recibidos de la catedra antes de persistirlos
 * ("validar los datos recibidos en cada limite de la aplicacion").
 * Un dato invalido aborta la unidad de trabajo completa: no se aplica a medias.
 */
public final class CatalogDataValidator {

    private CatalogDataValidator() {}

    public static void validateCategories(Collection<CategoryDto> items) {
        for (CategoryDto c : items) {
            require(c.id() != null, "categoria sin id");
            require(notBlank(c.name()), "categoria " + c.id() + " sin nombre");
            require(c.enabled() != null, "categoria " + c.id() + " sin enabled");
        }
    }

    public static void validateProfessionals(Collection<ProfessionalDto> items) {
        for (ProfessionalDto p : items) {
            require(p.id() != null, "profesional sin id");
            require(p.categoryId() != null, "profesional " + p.id() + " sin categoryId");
            require(notBlank(p.firstName()) && notBlank(p.lastName()), "profesional " + p.id() + " sin nombre");
            require(p.enabled() != null, "profesional " + p.id() + " sin enabled");
        }
    }

    public static void validateSchedules(Collection<WeeklyScheduleDto> items) {
        for (WeeklyScheduleDto s : items) {
            require(s.id() != null, "horario sin id");
            require(s.professionalId() != null, "horario " + s.id() + " sin professionalId");
            require(s.dayOfWeek() != null, "horario " + s.id() + " sin dayOfWeek");
            require(s.startTime() != null && s.endTime() != null, "horario " + s.id() + " sin horas");
            require(s.startTime().isBefore(s.endTime()), "horario " + s.id() + " con inicio >= fin");
            require(s.slotDurationMinutes() != null && s.slotDurationMinutes() > 0,
                "horario " + s.id() + " con duracion de turno invalida");
            require(s.enabled() != null, "horario " + s.id() + " sin enabled");
        }
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new CatalogInconsistencyException("Dato de catalogo invalido: " + message);
        }
    }
}
