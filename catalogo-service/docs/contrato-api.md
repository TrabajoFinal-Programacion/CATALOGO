# Contrato de la API de catálogo

La consumen la app KMP y el servicio de turnos. Versión 1.

## Autenticación

Todas las rutas exigen `Authorization: Bearer <jwt>`, con el JWT de **usuario final** emitido por turnos-service:

- firma RS256 válida (clave pública de turnos),
- `exp` vigente, `iss = turnos-service`, `aud = turnos-app`,
- claim `auth` con `ROLE_USER` (o `ROLE_ADMIN` para `POST /api/sync/**`).

Turnos **propaga el mismo JWT** que recibió de la app (ver la ADR 0001 en turnos-service). El JWT técnico de la cátedra nunca se usa con este servicio.

## Errores

`application/problem+json` con un `code` estable:

| HTTP | `code` | Cuándo |
| --- | --- | --- |
| 400 | `INVALID_FILTER` | combinación o formato de filtros inválido |
| 400 | `VALIDATION_ERROR` | tipo de parámetro inválido (por ejemplo `dayOfWeek=LUNES`) |
| 401 | `UNAUTHORIZED` | sin token, o token inválido o vencido |
| 403 | `FORBIDDEN` | rol insuficiente |
| 404 | `PROFESSIONAL_NOT_FOUND` | el profesional no existe en la copia local |
| 503 | `CATALOG_NOT_SYNCHRONIZED` | la copia local todavía no se inicializó |
| 503 | `SYNC_FAILED` | la sincronización pedida por un administrador no pudo completarse |

**Para turnos:** un 503 o un error de conexión significan que no se puede validar la información vigente. En ese caso, turnos **no inicia** la reserva.

## `GET /api/categories`

Parámetro opcional: `enabled` (`true`/`false`).

```json
[
  { "id": 1, "name": "Clinica medica", "description": "Atencion clinica general", "enabled": true }
]
```

## `GET /api/professionals`

| Parámetro | Tipo | Descripción |
| --- | --- | --- |
| `categoryId` | long | categoría exacta |
| `name` | string (máx. 100) | parte del nombre, del apellido o de "nombre apellido"; no distingue mayúsculas |
| `enabled` | boolean | estado habilitado del profesional |
| `dayOfWeek` | `MONDAY`..`SUNDAY` | **disponibilidad**: tiene agenda habilitada ese día |
| `date` | `yyyy-MM-dd` | igual que `dayOfWeek`, calculado a partir de la fecha |
| `from`, `to` | `HH:mm` o `HH:mm:ss` | la franja se superpone con `[from, to)`; requieren `dayOfWeek` o `date` |
| `page`, `size` | int | paginación (`size` máx. 100, por defecto 20) |

**Disponibilidad**, en este servicio, significa: el profesional está habilitado, su categoría está habilitada y tiene al menos una franja semanal habilitada ese día (y en esa hora, si se pide). Los turnos ya ocupados no los conoce el catálogo: los descuenta el servicio de turnos con las ocupaciones de la cátedra.

Orden: apellido, nombre, id.

```json
{
  "content": [
    {
      "id": 15, "firstName": "Ana", "lastName": "Gomez", "enabled": true,
      "categoryId": 1, "category": { "id": 1, "name": "Clinica medica", "enabled": true }
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1,
  "catalogVersion": 7
}
```

## `GET /api/professionals/{id}`

Profesional con su agenda semanal, ordenada por día de la semana y hora. Lo usa turnos para construir la disponibilidad y para validar antes de crear un hold.

```json
{
  "id": 15, "firstName": "Ana", "lastName": "Gomez", "enabled": true,
  "categoryId": 1, "category": { "id": 1, "name": "Clinica medica", "enabled": true },
  "schedules": [
    { "id": 21, "dayOfWeek": "MONDAY", "startTime": "09:00:00", "endTime": "12:00:00",
      "slotDurationMinutes": 30, "enabled": true }
  ],
  "catalogVersion": 7
}
```

Los profesionales y las franjas deshabilitados **se incluyen** con `enabled: false`: quien consume decide. Turnos no ofrece turnos de un profesional, una categoría o una franja deshabilitados.

## `GET /api/sync/status`

```json
{
  "localVersion": 7, "remoteVersion": 7, "status": "UP_TO_DATE",
  "lastSyncAt": "2026-10-22T13:00:00Z", "lastFullSyncAt": "2026-10-22T12:00:00Z",
  "lastError": null
}
```

`status`: `EMPTY`, `SYNCING`, `UP_TO_DATE`, `ERROR`.

## `POST /api/sync/run` y `POST /api/sync/full` (ROLE_ADMIN)

Devuelven el resultado:

```json
{ "type": "FULL", "previousVersion": 5, "currentVersion": 7, "reason": "FORZADA" }
```

`type`: `NONE`, `INCREMENTAL`, `FULL`.
