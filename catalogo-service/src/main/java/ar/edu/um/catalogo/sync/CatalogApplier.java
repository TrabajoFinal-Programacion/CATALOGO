package ar.edu.um.catalogo.sync;

import ar.edu.um.catalogo.catalog.Professional;
import ar.edu.um.catalogo.catalog.ProfessionalCategory;
import ar.edu.um.catalogo.catalog.ProfessionalCategoryRepository;
import ar.edu.um.catalogo.catalog.ProfessionalRepository;
import ar.edu.um.catalogo.catalog.WeeklySchedule;
import ar.edu.um.catalogo.catalog.WeeklyScheduleRepository;
import ar.edu.um.catalogo.catedra.dto.CategoryDto;
import ar.edu.um.catalogo.catedra.dto.ProfessionalDto;
import ar.edu.um.catalogo.catedra.dto.SnapshotDto;
import ar.edu.um.catalogo.catedra.dto.WeeklyScheduleDto;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica datos de catalogo a la base local. Cada metodo es UNA transaccion que incluye
 * los datos y la version local: o queda todo aplicado con su version, o no queda nada.
 * Mientras la transaccion no termina, las busquedas siguen viendo la version anterior completa
 * (aislamiento de PostgreSQL).
 */
@Service
public class CatalogApplier {

    private final ProfessionalCategoryRepository categories;
    private final ProfessionalRepository professionals;
    private final WeeklyScheduleRepository schedules;
    private final SyncStateRepository syncStateRepository;
    private final Clock clock;

    public CatalogApplier(ProfessionalCategoryRepository categories, ProfessionalRepository professionals,
                          WeeklyScheduleRepository schedules, SyncStateRepository syncStateRepository, Clock clock) {
        this.categories = categories;
        this.professionals = professionals;
        this.schedules = schedules;
        this.syncStateRepository = syncStateRepository;
        this.clock = clock;
    }

    /**
     * Reemplaza la copia local por el snapshot y registra snapshotVersion como version local.
     * El snapshot trae entidades habilitadas y deshabilitadas: lo que no viene, no existe en la fuente.
     */
    @Transactional
    public void applySnapshot(SnapshotDto snapshot) {
        if (snapshot.snapshotVersion() == null || snapshot.snapshotVersion() < 0) {
            throw new CatalogInconsistencyException("Snapshot sin snapshotVersion valida");
        }
        CatalogDataValidator.validateCategories(snapshot.professionalCategories());
        CatalogDataValidator.validateProfessionals(snapshot.professionals());
        CatalogDataValidator.validateSchedules(snapshot.weeklySchedules());

        SyncState state = lockState();

        deleteAllExcept(snapshot.weeklySchedules().stream().map(WeeklyScheduleDto::id).collect(Collectors.toSet()),
            schedules::deleteByIdNotIn, schedules::deleteAllInBatch);
        deleteAllExcept(snapshot.professionals().stream().map(ProfessionalDto::id).collect(Collectors.toSet()),
            professionals::deleteByIdNotIn, professionals::deleteAllInBatch);
        deleteAllExcept(snapshot.professionalCategories().stream().map(CategoryDto::id).collect(Collectors.toSet()),
            categories::deleteByIdNotIn, categories::deleteAllInBatch);

        upsert(snapshot.professionalCategories(), snapshot.professionals(), snapshot.weeklySchedules());

        Instant now = clock.instant();
        state.setLocalVersion(snapshot.snapshotVersion());
        state.setLastFullSyncAt(now);
        state.setLastSyncAt(now);
        state.setUpdatedAt(now);
    }

    /**
     * Aplica una version incremental. Solo avanza si la version local es exactamente la anterior
     * (compare-and-set bajo SELECT FOR UPDATE): nunca se saltea ni se repite una version.
     *
     * @return false si la version local ya no era expectedPrevious (otro proceso avanzo); no aplica nada.
     */
    @Transactional
    public boolean applyVersion(long version, long expectedPrevious, List<CategoryDto> changedCategories,
                                List<ProfessionalDto> changedProfessionals, List<WeeklyScheduleDto> changedSchedules) {
        CatalogDataValidator.validateCategories(changedCategories);
        CatalogDataValidator.validateProfessionals(changedProfessionals);
        CatalogDataValidator.validateSchedules(changedSchedules);

        SyncState state = lockState();
        if (!Objects.equals(state.getLocalVersion(), expectedPrevious)) {
            return false;
        }
        upsert(changedCategories, changedProfessionals, changedSchedules);

        Instant now = clock.instant();
        state.setLocalVersion(version);
        state.setLastSyncAt(now);
        state.setUpdatedAt(now);
        return true;
    }

    private void upsert(List<CategoryDto> cats, List<ProfessionalDto> pros, List<WeeklyScheduleDto> sch) {
        // save() con ID asignado hace merge: inserta si no existe, actualiza si existe.
        categories.saveAll(cats.stream().map(ProfessionalCategory::from).toList());
        professionals.saveAll(pros.stream().map(Professional::from).toList());
        schedules.saveAll(sch.stream().map(WeeklySchedule::from).toList());
    }

    private static void deleteAllExcept(Set<Long> keep, java.util.function.ToIntFunction<Collection<Long>> deleteNotIn,
                                        Runnable deleteAll) {
        // "NOT IN ()" no es SQL valido: con el conjunto vacio se borra todo.
        if (keep.isEmpty()) {
            deleteAll.run();
        } else {
            deleteNotIn.applyAsInt(keep);
        }
    }

    private SyncState lockState() {
        return syncStateRepository.findForUpdate()
            .orElseThrow(() -> new IllegalStateException("Falta la fila de sync_state (migracion V1)"));
    }
}
