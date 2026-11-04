package ar.edu.um.catalogo.catalog;

import ar.edu.um.catalogo.catalog.dto.CategoryRef;
import ar.edu.um.catalogo.catalog.dto.CategoryResponse;
import ar.edu.um.catalogo.catalog.dto.PageResponse;
import ar.edu.um.catalogo.catalog.dto.ProfessionalDetailResponse;
import ar.edu.um.catalogo.catalog.dto.ProfessionalSummaryResponse;
import ar.edu.um.catalogo.catalog.dto.ScheduleResponse;
import ar.edu.um.catalogo.sync.SyncStateService;
import ar.edu.um.catalogo.web.error.ApiException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consultas sobre la copia local del catalogo.
 * Si la copia nunca se inicializo, responde 503 CATALOG_NOT_SYNCHRONIZED: no se puede validar
 * informacion vigente sin datos, y una lista vacia seria enganosa.
 */
@Service
@Transactional(readOnly = true)
public class CatalogQueryService {

    public static final int MAX_PAGE_SIZE = 100;
    private static final Sort PROFESSIONAL_ORDER = Sort.by("lastName", "firstName", "id");

    private final ProfessionalCategoryRepository categories;
    private final ProfessionalRepository professionals;
    private final WeeklyScheduleRepository schedules;
    private final SyncStateService syncState;

    public CatalogQueryService(ProfessionalCategoryRepository categories, ProfessionalRepository professionals,
                               WeeklyScheduleRepository schedules, SyncStateService syncState) {
        this.categories = categories;
        this.professionals = professionals;
        this.schedules = schedules;
        this.syncState = syncState;
    }

    public List<CategoryResponse> categories(Boolean enabled) {
        requireSynchronized();
        List<ProfessionalCategory> result = enabled == null
            ? categories.findAllByOrderByNameAsc()
            : categories.findAllByEnabledOrderByNameAsc(enabled);
        return result.stream().map(CategoryResponse::from).toList();
    }

    public PageResponse<ProfessionalSummaryResponse> searchProfessionals(ProfessionalSearchCriteria criteria,
                                                                         int page, int size) {
        long version = requireSynchronized();
        Specification<Professional> spec = Specification.allOf(
            ProfessionalSpecifications.category(criteria.categoryId()),
            ProfessionalSpecifications.nameContains(criteria.name()),
            ProfessionalSpecifications.enabled(criteria.enabled()),
            ProfessionalSpecifications.availableOn(criteria.availableDay(), criteria.availableFrom(),
                criteria.availableTo()));
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Professional> result = professionals.findAll(spec, PageRequest.of(Math.max(page, 0), safeSize,
            PROFESSIONAL_ORDER));

        Set<Long> categoryIds = result.stream().map(Professional::getCategoryId).collect(Collectors.toSet());
        Map<Long, ProfessionalCategory> categoryById = categories.findAllById(categoryIds).stream()
            .collect(Collectors.toMap(ProfessionalCategory::getId, Function.identity()));

        List<ProfessionalSummaryResponse> content = result.stream()
            .map(p -> ProfessionalSummaryResponse.from(p, categoryById.get(p.getCategoryId())))
            .toList();
        return PageResponse.of(result, content, version);
    }

    public ProfessionalDetailResponse professional(Long id) {
        long version = requireSynchronized();
        Professional p = professionals.findById(id)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PROFESSIONAL_NOT_FOUND",
                "No existe el profesional " + id));
        ProfessionalCategory category = categories.findById(p.getCategoryId()).orElse(null);
        List<ScheduleResponse> agenda = schedules.findAllByProfessionalId(id).stream()
            .sorted(Comparator.comparing(WeeklySchedule::getDayOfWeek).thenComparing(WeeklySchedule::getStartTime))
            .map(ScheduleResponse::from)
            .toList();
        return new ProfessionalDetailResponse(p.getId(), p.getFirstName(), p.getLastName(), p.isEnabled(),
            p.getCategoryId(), CategoryRef.from(category), agenda, version);
    }

    private long requireSynchronized() {
        Long version = syncState.localVersion();
        if (version == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CATALOG_NOT_SYNCHRONIZED",
                "El catalogo local todavia no fue sincronizado");
        }
        return version;
    }
}
