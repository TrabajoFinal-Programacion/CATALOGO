package ar.edu.um.catalogo;

import ar.edu.um.catalogo.catedra.dto.CategoryDto;
import ar.edu.um.catalogo.catedra.dto.ChangeSetDto;
import ar.edu.um.catalogo.catedra.dto.ProfessionalDto;
import ar.edu.um.catalogo.catedra.dto.SnapshotDto;
import ar.edu.um.catalogo.catedra.dto.WeeklyScheduleDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Datos de prueba ficticios y helpers para simular lo que publica la catedra en Redis.
 *
 * Catalogo base:
 *   categoria 1 "Clinica medica" (habilitada), categoria 2 "Pediatria" (habilitada),
 *   categoria 3 "Dermatologia" (deshabilitada)
 *   profesional 15 Ana Gomez     (cat 1, habilitada)  lunes 09-12, miercoles 14-18
 *   profesional 16 Bruno Diaz    (cat 2, habilitado)  martes 08-10
 *   profesional 17 Carla Gomez   (cat 1, deshabilitada) lunes 09-12
 *   profesional 18 Dario Ruiz    (cat 3, habilitado, categoria deshabilitada) lunes 09-12
 */
public final class CatalogFixtures {

    public static final Instant T0 = Instant.parse("2026-07-01T12:00:00Z");

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;

    public CatalogFixtures(StringRedisTemplate redis, ObjectMapper objectMapper, JdbcTemplate jdbc) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.jdbc = jdbc;
    }

    // --- datos ---

    public static CategoryDto category(long id, String name, boolean enabled) {
        return new CategoryDto(id, name, "Descripcion " + name, enabled, T0, T0);
    }

    public static ProfessionalDto professional(long id, long categoryId, String first, String last, boolean enabled) {
        return new ProfessionalDto(id, categoryId, first, last, enabled, T0, T0);
    }

    public static WeeklyScheduleDto schedule(long id, long professionalId, DayOfWeek day, String start, String end) {
        return new WeeklyScheduleDto(id, professionalId, day, LocalTime.parse(start), LocalTime.parse(end), 30, true,
            T0, T0);
    }

    public static List<CategoryDto> baseCategories() {
        return List.of(category(1, "Clinica medica", true), category(2, "Pediatria", true),
            category(3, "Dermatologia", false));
    }

    public static List<ProfessionalDto> baseProfessionals() {
        return List.of(
            professional(15, 1, "Ana", "Gomez", true),
            professional(16, 2, "Bruno", "Diaz", true),
            professional(17, 1, "Carla", "Gomez", false),
            professional(18, 3, "Dario", "Ruiz", true));
    }

    public static List<WeeklyScheduleDto> baseSchedules() {
        return List.of(
            schedule(21, 15, DayOfWeek.MONDAY, "09:00", "12:00"),
            schedule(22, 15, DayOfWeek.WEDNESDAY, "14:00", "18:00"),
            schedule(23, 16, DayOfWeek.TUESDAY, "08:00", "10:00"),
            schedule(24, 17, DayOfWeek.MONDAY, "09:00", "12:00"),
            schedule(25, 18, DayOfWeek.MONDAY, "09:00", "12:00"));
    }

    public static SnapshotDto snapshot(long version) {
        return new SnapshotDto(version, T0, baseCategories(), baseProfessionals(), baseSchedules());
    }

    // --- estado limpio entre tests ---

    public void reset() {
        jdbc.execute("TRUNCATE weekly_schedule, professional, professional_category, processed_event");
        jdbc.update("UPDATE sync_state SET local_version = NULL, remote_version = NULL, status = 'EMPTY', "
            + "last_sync_at = NULL, last_full_sync_at = NULL, last_error = NULL");
        redis.execute((RedisCallback<Void>) (RedisConnection connection) -> {
            connection.serverCommands().flushAll();
            return null;
        });
    }

    // --- Redis "de la catedra" ---

    public void publishVersions(long current, long oldest) {
        redis.opsForHash().putAll("catedra:sync:metadata", Map.of(
            "currentVersion", String.valueOf(current),
            "oldestAvailableVersion", String.valueOf(oldest),
            "publishedAt", T0.toString()));
        redis.opsForValue().set("catedra:sync:current-version", String.valueOf(current));
        redis.opsForValue().set("catedra:sync:oldest-available-version", String.valueOf(oldest));
    }

    public void publishChanges(long version, List<Long> categories, List<Long> professionals, List<Long> schedules) {
        ChangeSetDto changes = new ChangeSetDto(version, new ChangeSetDto.Changes(categories, professionals, schedules));
        redis.opsForValue().set("catedra:sync:changes:" + version, json(changes));
    }

    /** Publica el estado vigente de las entidades en los Hashes (como hace la catedra). */
    public void publishEntities(List<CategoryDto> categories, List<ProfessionalDto> professionals,
                                List<WeeklyScheduleDto> schedules) {
        categories.forEach(c -> redis.opsForHash().put("catedra:sync:professional-categories",
            String.valueOf(c.id()), json(c)));
        professionals.forEach(p -> redis.opsForHash().put("catedra:sync:professionals",
            String.valueOf(p.id()), json(p)));
        schedules.forEach(s -> redis.opsForHash().put("catedra:sync:weekly-schedules",
            String.valueOf(s.id()), json(s)));
    }

    public void publishRaw(String hash, String field, String value) {
        redis.opsForHash().put(hash, field, value);
    }

    public String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
