package ar.edu.um.catalogo.catalog;

import ar.edu.um.catalogo.catalog.dto.CategoryResponse;
import ar.edu.um.catalogo.catalog.dto.PageResponse;
import ar.edu.um.catalogo.catalog.dto.ProfessionalDetailResponse;
import ar.edu.um.catalogo.catalog.dto.ProfessionalSummaryResponse;
import ar.edu.um.catalogo.web.error.ApiException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Busqueda sobre la copia local del catalogo. Requiere ROLE_USER (JWT de usuario final,
 * enviado por la app KMP o propagado por el servicio de turnos).
 */
@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogQueryService queryService;

    public CatalogController(CatalogQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories(@RequestParam(required = false) Boolean enabled) {
        return queryService.categories(enabled);
    }

    /**
     * Filtros combinables:
     * - categoryId: categoria exacta
     * - name: parte del nombre o apellido, sin distinguir mayusculas
     * - enabled: estado habilitado del profesional
     * - dayOfWeek o date (yyyy-MM-dd): disponibilidad de agenda ese dia
     * - from / to (HH:mm o HH:mm:ss): franja horaria dentro de ese dia; requieren dayOfWeek o date
     */
    @GetMapping("/professionals")
    public PageResponse<ProfessionalSummaryResponse> professionals(
        @RequestParam(required = false) Long categoryId,
        @RequestParam(required = false) String name,
        @RequestParam(required = false) Boolean enabled,
        @RequestParam(required = false) DayOfWeek dayOfWeek,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        @RequestParam(required = false) String from,
        @RequestParam(required = false) String to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

        if (name != null && name.length() > 100) {
            throw badRequest("INVALID_FILTER", "name admite hasta 100 caracteres");
        }
        DayOfWeek day = dayOfWeek;
        if (date != null) {
            if (dayOfWeek != null && dayOfWeek != date.getDayOfWeek()) {
                throw badRequest("INVALID_FILTER", "date y dayOfWeek no coinciden");
            }
            day = date.getDayOfWeek();
        }
        LocalTime fromTime = parseTime(from, "from");
        LocalTime toTime = parseTime(to, "to");
        if ((fromTime != null || toTime != null) && day == null) {
            throw badRequest("INVALID_FILTER", "from/to requieren dayOfWeek o date");
        }
        if (fromTime != null && toTime != null && !fromTime.isBefore(toTime)) {
            throw badRequest("INVALID_FILTER", "from debe ser anterior a to");
        }
        return queryService.searchProfessionals(
            new ProfessionalSearchCriteria(categoryId, name, enabled, day, fromTime, toTime), page, size);
    }

    @GetMapping("/professionals/{id}")
    public ProfessionalDetailResponse professional(@PathVariable Long id) {
        return queryService.professional(id);
    }

    private static LocalTime parseTime(String value, String field) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim()); // ISO: acepta HH:mm y HH:mm:ss
        } catch (DateTimeParseException e) {
            throw badRequest("INVALID_FILTER", field + " debe tener formato HH:mm");
        }
    }

    private static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail);
    }
}
