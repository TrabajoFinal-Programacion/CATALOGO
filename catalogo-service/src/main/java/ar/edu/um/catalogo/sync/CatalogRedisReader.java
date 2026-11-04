package ar.edu.um.catalogo.sync;

import ar.edu.um.catalogo.catedra.dto.CategoryDto;
import ar.edu.um.catalogo.catedra.dto.ChangeSetDto;
import ar.edu.um.catalogo.catedra.dto.ProfessionalDto;
import ar.edu.um.catalogo.catedra.dto.WeeklyScheduleDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Lectura del catalogo publicado por la catedra en Redis (namespace catedra:sync:*, solo lectura).
 * Las fallas de conexion se propagan como DataAccessException (error de infraestructura);
 * los datos ausentes o invalidos, como CatalogInconsistencyException (requieren snapshot).
 */
@Component
public class CatalogRedisReader {

    static final String METADATA = "catedra:sync:metadata";
    static final String CURRENT_VERSION = "catedra:sync:current-version";
    static final String OLDEST_VERSION = "catedra:sync:oldest-available-version";
    static final String CHANGES_PREFIX = "catedra:sync:changes:";
    static final String CATEGORIES = "catedra:sync:professional-categories";
    static final String PROFESSIONALS = "catedra:sync:professionals";
    static final String SCHEDULES = "catedra:sync:weekly-schedules";

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public CatalogRedisReader(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    /** Lee las versiones del Hash de metadata (una sola lectura); si falta, usa las claves sueltas. */
    public RemoteVersions readVersions() {
        Map<Object, Object> metadata = redis.opsForHash().entries(METADATA);
        Object current = metadata.get("currentVersion");
        Object oldest = metadata.get("oldestAvailableVersion");
        if (current == null || oldest == null) {
            current = redis.opsForValue().get(CURRENT_VERSION);
            oldest = redis.opsForValue().get(OLDEST_VERSION);
        }
        if (current == null || oldest == null) {
            throw new CatalogInconsistencyException("Redis no informa la version publicada del catalogo");
        }
        return new RemoteVersions(parseLong(current, "currentVersion"), parseLong(oldest, "oldestAvailableVersion"));
    }

    /** changes:{version}. Si la clave no existe, hay una discontinuidad. */
    public ChangeSetDto readChanges(long version) {
        String json = redis.opsForValue().get(CHANGES_PREFIX + version);
        if (json == null) {
            throw new CatalogInconsistencyException("No existe " + CHANGES_PREFIX + version);
        }
        ChangeSetDto changes = parse(json, ChangeSetDto.class, CHANGES_PREFIX + version);
        if (changes.version() == null || changes.version() != version) {
            throw new CatalogInconsistencyException(CHANGES_PREFIX + version + " declara otra version: " + changes.version());
        }
        return changes;
    }

    public List<CategoryDto> readCategories(Collection<Long> ids) {
        return readEntities(CATEGORIES, ids, CategoryDto.class, CategoryDto::id);
    }

    public List<ProfessionalDto> readProfessionals(Collection<Long> ids) {
        return readEntities(PROFESSIONALS, ids, ProfessionalDto.class, ProfessionalDto::id);
    }

    public List<WeeklyScheduleDto> readSchedules(Collection<Long> ids) {
        return readEntities(SCHEDULES, ids, WeeklyScheduleDto.class, WeeklyScheduleDto::id);
    }

    /**
     * Lee el estado vigente de cada ID con un solo HMGET.
     * Las bajas son logicas (enabled=false): un ID informado como cambiado que no esta en el Hash
     * es una inconsistencia, no una baja.
     */
    private <T> List<T> readEntities(String key, Collection<Long> ids, Class<T> type, Function<T, Long> idOf) {
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Object> fields = ids.stream().map(id -> (Object) String.valueOf(id)).toList();
        List<Object> values = redis.opsForHash().multiGet(key, fields);
        List<T> result = new ArrayList<>(ids.size());
        int i = 0;
        for (Long id : ids) {
            Object json = values.get(i++);
            if (json == null) {
                throw new CatalogInconsistencyException(key + " no contiene el ID " + id);
            }
            T entity = parse(json.toString(), type, key + "[" + id + "]");
            if (!id.equals(idOf.apply(entity))) {
                throw new CatalogInconsistencyException(key + "[" + id + "] contiene otro ID: " + idOf.apply(entity));
            }
            result.add(entity);
        }
        return result;
    }

    private <T> T parse(String json, Class<T> type, String source) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new CatalogInconsistencyException("JSON invalido en " + source, e);
        }
    }

    private static long parseLong(Object value, String name) {
        try {
            return Long.parseLong(value.toString().trim());
        } catch (NumberFormatException e) {
            throw new CatalogInconsistencyException(name + " no es numerico: " + value, e);
        }
    }
}
