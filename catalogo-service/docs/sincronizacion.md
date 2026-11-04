# Estrategia de sincronización del catálogo

## Fuentes

| Fuente | Rol |
| --- | --- |
| Kafka `catedra.catalog.{groupId}` | **notificación** de una nueva versión (`CatalogUpdated`). No trae datos. |
| Redis `catedra:sync:*` | versión publicada, ventana de versiones disponibles, cambios por versión (IDs) y estado vigente de cada entidad |
| REST `GET /api/synchronization/snapshot` | catálogo completo con su `snapshotVersion` |

## Cuándo se sincroniza

`CatalogSyncService.synchronize()` se ejecuta desde tres lugares:

1. **Kafka:** al recibir un `CatalogUpdated` cuya `newVersion` es mayor que la local.
2. **Reconciliación periódica** (`SyncScheduler`, 5 s después de arrancar y luego cada 60 s). Cubre el arranque con la base vacía, el reinicio con cambios pendientes, las notificaciones perdidas y el reintento después de un error.
3. **Endpoint administrativo** (`POST /api/sync/run` o `/api/sync/full`).

El método es `synchronized`: nunca corren dos sincronizaciones a la vez dentro del servicio.

## Decisión (`SyncPlanner`, función pura)

```
local = versión aplicada en la base (NULL = vacía)
current, oldest = catedra:sync:metadata

local == NULL        -> SNAPSHOT  (BASE_VACIA)
local >  current     -> SNAPSHOT  (VERSION_LOCAL_ADELANTADA: el estado local no puede verificarse)
local == current     -> nada
local <  oldest      -> SNAPSHOT  (FUERA_DE_VENTANA)
en otro caso         -> INCREMENTAL de local+1 a current
```

Con local = 3, current = 7 y oldest = 4 (ejemplo 18.2 del contrato) **no** se empieza en `changes:4`: falta la versión que conecta el estado local, así que se hace un snapshot.

## Incremental

Para cada versión `v` desde `local+1` hasta `current`, en orden:

1. `GET catedra:sync:changes:{v}`. Si no existe o declara otra versión → **discontinuidad**.
2. `HMGET` del estado **vigente** de cada ID afectado en el Hash correspondiente. Si falta un ID o su JSON es inválido → **discontinuidad**.
3. Validación de los datos (campos obligatorios, inicio < fin, duración > 0).
4. **Una transacción**: `SELECT … FOR UPDATE` sobre `sync_state`, comprobar que `local_version = v-1` (compare-and-set), upsert de las entidades y `local_version = v`.

Ante una discontinuidad, las versiones que ya se aplicaron quedan aplicadas y se continúa con un snapshot.

Los Hashes guardan el estado **actual**, no el de cada versión. Al aplicar la versión `v` puede guardarse un dato que ya pertenece a una versión posterior. No hay problema, porque el resultado converge siempre al estado vigente, y la versión local solo avanza a `v` después de que `v` quedó aplicada completa.

## Snapshot

1. `GET /api/synchronization/snapshot` con el JWT técnico. Timeouts de 3 s (conexión) y 20 s (lectura), hasta 3 intentos con espera creciente solo ante fallas transitorias. Un 4xx no se reintenta.
2. Validación de todos los datos **antes** de escribir.
3. **Una transacción**: se borra lo que no viene en el snapshot (el snapshot incluye habilitados y deshabilitados, así que lo ausente ya no existe en la fuente), se hace upsert del resto y `local_version = snapshotVersion`.

Mientras la transacción no termina, las búsquedas siguen viendo la versión anterior completa (aislamiento de PostgreSQL).

## Errores

| Situación | Resultado |
| --- | --- |
| Redis o la cátedra no disponibles | `status = ERROR` y `last_error`; la copia local queda en su última versión completa; se reintenta en la próxima reconciliación |
| Redis sin metadata o con datos inválidos | snapshot |
| Snapshot inválido | `ERROR`; no se aplica nada y no se encadenan snapshots |
| Error en el procesamiento de un mensaje Kafka | se reintenta 3 veces cada 5 s; después se confirma el offset y la reconciliación recupera el atraso (no se bloquea el topic ni hay bucles infinitos) |

El estado se consulta en `GET /api/sync/status`. El resultado de cada sincronización también se escribe en `alumnos:{groupId}:diagnostics:last-sync` (Redis, namespace privado, TTL 7 días), solo como diagnóstico.

## Idempotencia y mensajes duplicados, perdidos o fuera de orden

| Caso | Cómo se resuelve |
| --- | --- |
| **Duplicado** (mismo `eventId`) | `processed_event` guarda los `eventId` procesados. Uno repetido no dispara nada y solo incrementa `duplicate_count`, para que el duplicado quede observable. |
| **Evento nuevo de una versión ya aplicada** | si `newVersion <= local`, se registra y no se sincroniza |
| **Caída entre la sincronización y el registro del evento** | el `eventId` se registra **después** de sincronizar; Kafka lo vuelve a entregar y la sincronización repetida no cambia el resultado |
| **Notificación perdida** | la reconciliación periódica compara la versión local con la publicada |
| **Fuera de orden** | el evento no se usa como dato: siempre se aplica `local+1 … current` en orden, desde Redis |
| **Dos sincronizaciones simultáneas** | método `synchronized` y, además, compare-and-set de la versión bajo `SELECT … FOR UPDATE` |
