# catalogo-service

Servicio de **catálogo y sincronización** del proyecto integrador 2026. Mantiene una copia local de las categorías, los profesionales y los horarios semanales que publica la cátedra, la mantiene actualizada y resuelve las búsquedas sobre esa copia.

> Estado: **Etapa 2**. Sincronización completa (snapshot REST) e incremental (Kafka + Redis), recuperación ante discontinuidades y búsqueda con filtros.

## Requisitos

- Java 21
- Maven 3.9+
- Docker (infraestructura local y tests con Testcontainers)
- Acceso de red al servicio de la cátedra (red de la facultad o VPN)

La infraestructura (Docker Compose) está en el repo `turnos-service`, carpeta `deploy/`.

## Configuración

Todo se configura con variables de entorno. Ningún secreto está en el código.

| Variable | Origen | Ejemplo |
| --- | --- | --- |
| `CATEDRA_API_URL` | cátedra (otro canal) | `http://<ip>:<puerto>` |
| `CATEDRA_TOKEN` | `id_token` de la cuenta técnica | JWT técnico (1 año) |
| `CATEDRA_GROUP_ID` | `integration.groupId` | `proyecto-juan-perez` |
| `CATEDRA_REDIS_HOST` / `CATEDRA_REDIS_PORT` | cátedra | IP y puerto publicados |
| `CATEDRA_REDIS_USERNAME` / `CATEDRA_REDIS_PASSWORD` | `integration.redisUsername` / `redisPassword` | |
| `CATEDRA_KAFKA_BOOTSTRAP_SERVERS` | cátedra | `<ip>:<puerto>` |
| `CATEDRA_KAFKA_CATALOG_TOPIC` | opcional | por defecto `catedra.catalog.{groupId}` |
| `CATEDRA_KAFKA_CONSUMER_GROUP` | opcional | por defecto `alumnos-{groupId}` |
| `SYNC_RECONCILE_INTERVAL` | opcional | `PT60S` |
| `CATALOGO_DB_URL` / `CATALOGO_DB_USER` / `CATALOGO_DB_PASSWORD` | propia | |
| `JWT_PUBLIC_KEY_LOCATION` | `generate-keys.sh` | `file:/run/secrets/jwt_public_key` |

Si falta `CATEDRA_API_URL`, `CATEDRA_TOKEN` o `CATEDRA_GROUP_ID`, el servicio **no arranca** e indica qué falta.

Si `integration` devuelve nombres internos de la cátedra (por ejemplo `redis.catedra.internal`), en las variables van la IP y los puertos publicados que informó la cátedra.

## Tests

```bash
mvn verify
```

Requiere Docker: levanta PostgreSQL, Redis y Kafka reales con Testcontainers. La API REST de la cátedra se reemplaza por un mock.

| Test | Qué demuestra |
| --- | --- |
| `SyncPlannerTest` | reglas de decisión: base vacía, al día, incremental, fuera de ventana (ejemplo 18.2 del contrato), versión local adelantada |
| `CatalogSyncIntegrationTest` | inicialización desde snapshot; actualización incremental de una y de varias versiones; recuperación por snapshot ante discontinuidad y ante versión fuera de ventana; un dato inválido no avanza la versión ni deja datos a medias; un snapshot inválido deja la copia intacta; sincronizar dos veces no cambia el resultado |
| `CatalogUpdatedListenerIntegrationTest` | un `CatalogUpdated` por Kafka dispara la sincronización; **un mensaje duplicado se procesa una sola vez**; los mensajes inválidos no bloquean el topic |
| `CatalogSearchIntegrationTest` | filtros por categoría, nombre, estado habilitado y disponibilidad (día, fecha, franja), combinados; 503 antes de la primera sincronización; la búsqueda no llama a la cátedra |
| `JwtValidationIntegrationTest` | solo se aceptan JWT de turnos; las operaciones de sincronización exigen `ROLE_ADMIN` |

## API

Todas las rutas requieren `Authorization: Bearer <jwt-usuario>`. El contrato completo, con ejemplos y errores, está en [`docs/contrato-api.md`](docs/contrato-api.md).

| Método | Ruta | Rol | Descripción |
| --- | --- | --- | --- |
| `GET` | `/api/categories?enabled=` | USER | categorías |
| `GET` | `/api/professionals?categoryId=&name=&enabled=&dayOfWeek=&date=&from=&to=&page=&size=` | USER | búsqueda con filtros |
| `GET` | `/api/professionals/{id}` | USER | profesional con agenda semanal |
| `GET` | `/api/sync/status` | USER | versión local, versión publicada, estado y último error |
| `POST` | `/api/sync/run` | ADMIN | reconciliar ahora |
| `POST` | `/api/sync/full` | ADMIN | reconstruir desde el snapshot |

## Sincronización

El detalle está en [`docs/sincronizacion.md`](docs/sincronizacion.md). En resumen:

1. **Kafka avisa, no informa datos.** `CatalogUpdated` solo dispara la comparación de versiones.
2. **La decisión se toma por versión.** Base vacía, versión fuera de ventana o versión local adelantada → snapshot. Si no, se aplican `changes:{local+1}` … `changes:{current}` en orden.
3. **Cada versión es una transacción** que incluye los datos y la versión local. Si algo falla, la versión no avanza y no quedan datos a medias.
4. **Discontinuidad → snapshot.** Si falta un `changes:{v}`, falta una entidad o un JSON es inválido, se reconstruye desde el snapshot.
5. **Reconciliación periódica** (cada 60 s por defecto). Recupera notificaciones perdidas y reintenta después de un error.

## Modelo de datos (PostgreSQL, propiedad exclusiva de este servicio)

| Tabla | Contenido |
| --- | --- |
| `professional_category` | categorías (ID de la cátedra) |
| `professional` | profesionales (ID de la cátedra), `category_id` |
| `weekly_schedule` | franjas semanales: día, inicio, fin, duración de turno |
| `sync_state` | una fila: versión local aplicada, última versión publicada vista, estado, último error |
| `processed_event` | `eventId` de Kafka ya procesados y cantidad de duplicados recibidos |

No hay claves foráneas entre las tablas del catálogo. Es una réplica: la integridad la garantiza la fuente, y una inconsistencia transitoria de la cátedra no debe impedir aplicar una versión. Las bajas son lógicas (`enabled = false`), igual que en la fuente.
