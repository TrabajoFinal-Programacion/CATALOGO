package ar.edu.um.catalogo.catalog;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;

/**
 * Filtros de busqueda de profesionales. Se resuelven en SQL sobre la copia local:
 * nunca se consulta a la catedra por una busqueda.
 * Cada metodo devuelve null cuando el filtro no se pide (Spring Data lo interpreta como "sin restriccion").
 */
public final class ProfessionalSpecifications {

    private ProfessionalSpecifications() {}

    public static Specification<Professional> category(Long categoryId) {
        return (root, query, cb) -> categoryId == null ? null : cb.equal(root.get("categoryId"), categoryId);
    }

    public static Specification<Professional> enabled(Boolean enabled) {
        return (root, query, cb) -> enabled == null ? null : cb.equal(root.get("enabled"), enabled);
    }

    /** Coincidencia parcial, sin distinguir mayusculas, en nombre, apellido o "nombre apellido". */
    public static Specification<Professional> nameContains(String name) {
        return (root, query, cb) -> {
            if (name == null || name.isBlank()) {
                return null;
            }
            String pattern = "%" + escapeLike(name.trim().toLowerCase(Locale.ROOT)) + "%";
            Expression<String> first = cb.lower(root.<String>get("firstName"));
            Expression<String> last = cb.lower(root.<String>get("lastName"));
            Expression<String> full = cb.concat(cb.concat(first, " "), last);
            return cb.or(
                cb.like(first, pattern, '\\'),
                cb.like(last, pattern, '\\'),
                cb.like(full, pattern, '\\'));
        };
    }

    /**
     * Disponibilidad segun las reglas de agenda del catalogo: el profesional y su categoria estan
     * habilitados y tienen al menos una franja habilitada ese dia que se superpone con [from, to).
     * Las ocupaciones reales (turnos tomados) las combina el servicio de turnos.
     */
    public static Specification<Professional> availableOn(DayOfWeek day, LocalTime from, LocalTime to) {
        return (root, query, cb) -> {
            if (day == null) {
                return null;
            }
            Subquery<Long> schedule = query.subquery(Long.class);
            Root<WeeklySchedule> s = schedule.from(WeeklySchedule.class);
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(cb.equal(s.get("professionalId"), root.get("id")));
            conditions.add(cb.isTrue(s.<Boolean>get("enabled")));
            conditions.add(cb.equal(s.get("dayOfWeek"), day));
            if (from != null) {
                conditions.add(cb.greaterThan(s.<LocalTime>get("endTime"), from));
            }
            if (to != null) {
                conditions.add(cb.lessThan(s.<LocalTime>get("startTime"), to));
            }
            schedule.select(s.<Long>get("id")).where(conditions.toArray(Predicate[]::new));

            Subquery<Long> category = query.subquery(Long.class);
            Root<ProfessionalCategory> c = category.from(ProfessionalCategory.class);
            category.select(c.<Long>get("id")).where(
                cb.equal(c.get("id"), root.get("categoryId")),
                cb.isTrue(c.<Boolean>get("enabled")));

            return cb.and(cb.isTrue(root.<Boolean>get("enabled")), cb.exists(schedule), cb.exists(category));
        };
    }

    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
