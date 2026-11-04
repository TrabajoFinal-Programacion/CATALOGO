package ar.edu.um.catalogo.sync;

import static ar.edu.um.catalogo.CatalogFixtures.category;
import static ar.edu.um.catalogo.CatalogFixtures.professional;
import static ar.edu.um.catalogo.CatalogFixtures.schedule;
import static ar.edu.um.catalogo.CatalogFixtures.snapshot;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.um.catalogo.CatalogFixtures;
import ar.edu.um.catalogo.IntegrationTest;
import ar.edu.um.catalogo.catalog.Professional;
import ar.edu.um.catalogo.catalog.ProfessionalCategoryRepository;
import ar.edu.um.catalogo.catalog.ProfessionalRepository;
import ar.edu.um.catalogo.catalog.WeeklyScheduleRepository;
import ar.edu.um.catalogo.catedra.CatedraUnavailableException;
import ar.edu.um.catalogo.catedra.dto.CategoryDto;
import ar.edu.um.catalogo.catedra.dto.ProfessionalDto;
import ar.edu.um.catalogo.catedra.dto.SnapshotDto;
import ar.edu.um.catalogo.catedra.dto.WeeklyScheduleDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Sincronizacion completa e incremental contra PostgreSQL y Redis reales.
 * Cubre las evidencias: inicializacion desde snapshot, actualizacion incremental y
 * recuperacion mediante snapshot luego de una discontinuidad.
 */
class CatalogSyncIntegrationTest extends IntegrationTest {

    @Autowired CatalogSyncService syncService;
    @Autowired CatalogApplier applier;
    @Autowired SyncStateService stateService;
    @Autowired ProfessionalCategoryRepository categories;
    @Autowired ProfessionalRepository professionals;
    @Autowired WeeklyScheduleRepository schedules;
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper objectMapper;
    @Autowired JdbcTemplate jdbc;

    CatalogFixtures fixtures;

    @BeforeEach
    void setUp() {
        fixtures = new CatalogFixtures(redis, objectMapper, jdbc);
        fixtures.reset();
    }

    @Test
    void emptyDatabaseIsInitializedFromSnapshot() {
        fixtures.publishVersions(7, 4);
        when(catedraClient.fetchSnapshot()).thenReturn(snapshot(7));

        SyncOutcome outcome = syncService.synchronize();

        assertThat(outcome.type()).isEqualTo(SyncPlan.Type.FULL);
        assertThat(outcome.reason()).isEqualTo("BASE_VACIA");
        assertThat(categories.count()).isEqualTo(3);
        assertThat(professionals.count()).isEqualTo(4);
        assertThat(schedules.count()).isEqualTo(5);
        SyncState state = stateService.current();
        assertThat(state.getLocalVersion()).isEqualTo(7);
        assertThat(state.getStatus()).isEqualTo(SyncStatus.UP_TO_DATE);
        assertThat(state.getLastFullSyncAt()).isNotNull();
        assertThat(state.getLastError()).isNull();
    }

    @Test
    void snapshotReplacesTheWholeCopyAndRemovesWhatIsNoLongerPublished() {
        applier.applySnapshot(snapshot(3));
        SnapshotDto smaller = new SnapshotDto(4L, CatalogFixtures.T0,
            List.of(category(1, "Clinica medica", true)),
            List.of(professional(15, 1, "Ana", "Gomez", true)),
            List.of(schedule(21, 15, DayOfWeek.MONDAY, "09:00", "12:00")));

        applier.applySnapshot(smaller);

        assertThat(categories.count()).isEqualTo(1);
        assertThat(professionals.findAll()).extracting(Professional::getId).containsExactly(15L);
        assertThat(schedules.count()).isEqualTo(1);
        assertThat(stateService.localVersion()).isEqualTo(4);
    }

    @Test
    void newVersionIsAppliedIncrementallyFromRedis() {
        applier.applySnapshot(snapshot(5));
        fixtures.publishVersions(6, 1);
        fixtures.publishChanges(6, List.of(), List.of(15L), List.of(26L));
        fixtures.publishEntities(List.of(),
            List.of(professional(15, 1, "Ana", "Gomez Perez", false)),
            List.of(schedule(26, 16, DayOfWeek.FRIDAY, "10:00", "13:00")));

        SyncOutcome outcome = syncService.synchronize();

        assertThat(outcome.type()).isEqualTo(SyncPlan.Type.INCREMENTAL);
        assertThat(outcome.previousVersion()).isEqualTo(5);
        assertThat(outcome.currentVersion()).isEqualTo(6);
        Professional ana = professionals.findById(15L).orElseThrow();
        assertThat(ana.getLastName()).isEqualTo("Gomez Perez");
        assertThat(ana.isEnabled()).isFalse(); // baja logica: sigue existiendo, deshabilitada
        assertThat(schedules.findById(26L)).isPresent();
        assertThat(stateService.localVersion()).isEqualTo(6);
        verify(catedraClient, never()).fetchSnapshot();
    }

    @Test
    void severalVersionsAreAppliedInOrder() {
        applier.applySnapshot(snapshot(5));
        fixtures.publishVersions(7, 1);
        fixtures.publishChanges(6, List.of(2L), List.of(), List.of());
        fixtures.publishChanges(7, List.of(4L), List.of(), List.of());
        fixtures.publishEntities(List.of(category(2, "Pediatria infantil", true), category(4, "Cardiologia", true)),
            List.of(), List.of());

        SyncOutcome outcome = syncService.synchronize();

        assertThat(outcome.type()).isEqualTo(SyncPlan.Type.INCREMENTAL);
        assertThat(outcome.currentVersion()).isEqualTo(7);
        assertThat(categories.findById(2L).orElseThrow().getName()).isEqualTo("Pediatria infantil");
        assertThat(categories.findById(4L)).isPresent();
    }

    /** Contrato 18.2: local 3, current 7, oldest 4 -> snapshot, sin intentar changes:4. */
    @Test
    void versionOutsideTheWindowIsRecoveredWithSnapshot() {
        applier.applySnapshot(snapshot(3));
        fixtures.publishVersions(7, 4);
        when(catedraClient.fetchSnapshot()).thenReturn(snapshot(7));

        SyncOutcome outcome = syncService.synchronize();

        assertThat(outcome.type()).isEqualTo(SyncPlan.Type.FULL);
        assertThat(outcome.reason()).isEqualTo("FUERA_DE_VENTANA");
        assertThat(stateService.localVersion()).isEqualTo(7);
        verify(catedraClient, times(1)).fetchSnapshot();
    }

    @Test
    void missingChangeKeyInsideTheWindowFallsBackToSnapshot() {
        applier.applySnapshot(snapshot(5));
        fixtures.publishVersions(7, 1);
        fixtures.publishChanges(6, List.of(), List.of(16L), List.of());
        fixtures.publishEntities(List.of(), List.of(professional(16, 2, "Bruno", "Diaz", true)), List.of());
        // changes:7 no existe: discontinuidad
        when(catedraClient.fetchSnapshot()).thenReturn(snapshot(7));

        SyncOutcome outcome = syncService.synchronize();

        assertThat(outcome.type()).isEqualTo(SyncPlan.Type.FULL);
        assertThat(outcome.reason()).isEqualTo("DISCONTINUIDAD");
        assertThat(stateService.localVersion()).isEqualTo(7);
    }

    @Test
    void invalidEntityDoesNotAdvanceTheVersionAndErrorIsReported() {
        applier.applySnapshot(snapshot(5));
        fixtures.publishVersions(6, 1);
        fixtures.publishChanges(6, List.of(), List.of(15L), List.of());
        fixtures.publishRaw("catedra:sync:professionals", "15", "{esto no es json");
        when(catedraClient.fetchSnapshot()).thenThrow(new CatedraUnavailableException("catedra caida", null));

        assertThatThrownBy(() -> syncService.synchronize()).isInstanceOf(SyncFailedException.class);

        SyncState state = stateService.current();
        assertThat(state.getLocalVersion()).isEqualTo(5); // no se aplico nada a medias
        assertThat(state.getStatus()).isEqualTo(SyncStatus.ERROR);
        assertThat(state.getLastError()).contains("catedra caida");
        assertThat(professionals.findById(15L).orElseThrow().getLastName()).isEqualTo("Gomez");
    }

    @Test
    void failedSnapshotLeavesTheLocalCopyIntact() {
        applier.applySnapshot(snapshot(5));
        when(catedraClient.fetchSnapshot()).thenReturn(new SnapshotDto(9L, CatalogFixtures.T0,
            List.of(new CategoryDto(1L, null, null, true, null, null)), // categoria sin nombre: invalida
            List.<ProfessionalDto>of(), List.<WeeklyScheduleDto>of()));

        assertThatThrownBy(() -> syncService.forceFullSync()).isInstanceOf(SyncFailedException.class);

        assertThat(stateService.localVersion()).isEqualTo(5);
        assertThat(professionals.count()).isEqualTo(4);
        assertThat(stateService.current().getStatus()).isEqualTo(SyncStatus.ERROR);
    }

    @Test
    void repeatingTheSynchronizationDoesNotChangeTheResult() {
        applier.applySnapshot(snapshot(5));
        fixtures.publishVersions(6, 1);
        fixtures.publishChanges(6, List.of(), List.of(15L), List.of());
        fixtures.publishEntities(List.of(), List.of(professional(15, 1, "Ana", "Gomez Perez", true)), List.of());

        syncService.synchronize();
        SyncOutcome second = syncService.synchronize();

        assertThat(second.type()).isEqualTo(SyncPlan.Type.NONE);
        assertThat(stateService.localVersion()).isEqualTo(6);
        assertThat(professionals.findById(15L).orElseThrow().getLastName()).isEqualTo("Gomez Perez");
    }

    @Test
    void redisWithoutMetadataUsesTheSnapshot() {
        when(catedraClient.fetchSnapshot()).thenReturn(snapshot(2));

        SyncOutcome outcome = syncService.synchronize();

        assertThat(outcome.type()).isEqualTo(SyncPlan.Type.FULL);
        assertThat(stateService.localVersion()).isEqualTo(2);
    }

    @Test
    void applyVersionRefusesToSkipVersions() {
        applier.applySnapshot(snapshot(5));

        boolean applied = applier.applyVersion(8, 7, List.of(), List.of(), List.of());

        assertThat(applied).isFalse();
        assertThat(stateService.localVersion()).isEqualTo(5);
    }

    @Test
    void successfulSyncWritesDiagnosticsInThePrivateNamespace() {
        fixtures.publishVersions(7, 4);
        when(catedraClient.fetchSnapshot()).thenReturn(snapshot(7));

        syncService.synchronize();

        assertThat(redis.opsForHash().get("alumnos:test-group:diagnostics:last-sync", "localVersion")).isEqualTo("7");
    }
}
