package ar.edu.um.catalogo.catalog;

import static ar.edu.um.catalogo.CatalogFixtures.snapshot;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.um.catalogo.CatalogFixtures;
import ar.edu.um.catalogo.IntegrationTest;
import ar.edu.um.catalogo.security.TestTokens;
import ar.edu.um.catalogo.sync.CatalogApplier;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Busquedas resueltas sobre la copia local, con los filtros obligatorios:
 * categoria, nombre, estado habilitado y disponibilidad.
 * La catedra no interviene: catedraClient es un mock y se verifica que no se llame.
 */
class CatalogSearchIntegrationTest extends IntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired CatalogApplier applier;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        new CatalogFixtures(redis, objectMapper, jdbc).reset();
    }

    @Test
    void searchBeforeFirstSynchronizationReturns503() throws Exception {
        search("/api/professionals")
            .andExpect(status().isServiceUnavailable())
            .andExpect(jsonPath("$.code").value("CATALOG_NOT_SYNCHRONIZED"));
    }

    @Test
    void listsAllProfessionalsOrderedByLastName() throws Exception {
        applier.applySnapshot(snapshot(7));

        search("/api/professionals")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.totalElements").value(4))
            .andExpect(jsonPath("$.catalogVersion").value(7))
            .andExpect(jsonPath("$.content[*].id", contains(16, 15, 17, 18)))
            .andExpect(jsonPath("$.content[0].category.name").value("Pediatria"));
        org.mockito.Mockito.verifyNoInteractions(catedraClient);
    }

    @Test
    void filtersByCategory() throws Exception {
        applier.applySnapshot(snapshot(7));
        search("/api/professionals?categoryId=1")
            .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(15, 17)));
    }

    @Test
    void filtersByPartialCaseInsensitiveName() throws Exception {
        applier.applySnapshot(snapshot(7));
        search("/api/professionals?name=GOM")
            .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(15, 17)));
        search("/api/professionals?name=ana gomez")
            .andExpect(jsonPath("$.content[*].id", contains(15)));
    }

    @Test
    void nameWithLikeWildcardsIsTreatedLiterally() throws Exception {
        applier.applySnapshot(snapshot(7));
        search("/api/professionals?name=_")
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersByEnabledState() throws Exception {
        applier.applySnapshot(snapshot(7));
        search("/api/professionals?enabled=false")
            .andExpect(jsonPath("$.content[*].id", contains(17)));
        search("/api/professionals?enabled=true")
            .andExpect(jsonPath("$.content[*].id", containsInAnyOrder(15, 16, 18)));
    }

    /** Lunes: 15, 17 y 18 tienen agenda, pero 17 esta deshabilitado y 18 tiene la categoria deshabilitada. */
    @Test
    void filtersByAvailabilityOnADay() throws Exception {
        applier.applySnapshot(snapshot(7));
        search("/api/professionals?dayOfWeek=MONDAY")
            .andExpect(jsonPath("$.content[*].id", contains(15)));
        search("/api/professionals?date=2026-09-15") // martes
            .andExpect(jsonPath("$.content[*].id", contains(16)));
        search("/api/professionals?dayOfWeek=SUNDAY")
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersByAvailabilityWithinATimeRange() throws Exception {
        applier.applySnapshot(snapshot(7));
        // Ana atiende los miercoles 14-18
        search("/api/professionals?dayOfWeek=WEDNESDAY&from=17:00&to=19:00")
            .andExpect(jsonPath("$.content[*].id", contains(15)));
        search("/api/professionals?dayOfWeek=WEDNESDAY&from=09:00&to=12:00")
            .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersCanBeCombined() throws Exception {
        applier.applySnapshot(snapshot(7));
        search("/api/professionals?categoryId=1&name=gomez&enabled=true&dayOfWeek=MONDAY")
            .andExpect(jsonPath("$.content[*].id", contains(15)));
    }

    @Test
    void invalidFiltersAreRejected() throws Exception {
        applier.applySnapshot(snapshot(7));
        search("/api/professionals?from=10:00").andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_FILTER"));
        search("/api/professionals?dayOfWeek=MONDAY&from=12:00&to=10:00").andExpect(status().isBadRequest());
        search("/api/professionals?dayOfWeek=LUNES").andExpect(status().isBadRequest());
        search("/api/professionals?date=2026-09-14&dayOfWeek=TUESDAY").andExpect(status().isBadRequest());
    }

    @Test
    void professionalDetailIncludesWeeklyAgendaInWeekOrder() throws Exception {
        applier.applySnapshot(snapshot(7));
        search("/api/professionals/15")
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.category.name").value("Clinica medica"))
            .andExpect(jsonPath("$.schedules[*].dayOfWeek", contains("MONDAY", "WEDNESDAY")))
            .andExpect(jsonPath("$.schedules[0].startTime").value("09:00:00"))
            .andExpect(jsonPath("$.schedules[0].slotDurationMinutes").value(30))
            .andExpect(jsonPath("$.catalogVersion").value(7));
    }

    @Test
    void unknownProfessionalReturns404() throws Exception {
        applier.applySnapshot(snapshot(7));
        search("/api/professionals/999")
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("PROFESSIONAL_NOT_FOUND"));
    }

    @Test
    void categoriesCanBeFilteredByEnabled() throws Exception {
        applier.applySnapshot(snapshot(7));
        search("/api/categories?enabled=true")
            .andExpect(jsonPath("$[*].name", contains("Clinica medica", "Pediatria")));
    }

    @Test
    void searchRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/professionals")).andExpect(status().isUnauthorized());
    }

    private ResultActions search(String url) throws Exception {
        return mvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.user()));
    }
}
