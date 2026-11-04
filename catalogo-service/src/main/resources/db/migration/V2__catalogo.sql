-- Copia local del catalogo publicado por la catedra.
-- Los IDs son los de la catedra (no autoincrementales): asi un cambio incremental
-- que informa "profesional 15" se aplica directamente sobre la fila 15.
--
-- No hay claves foraneas entre estas tablas a proposito: la copia replica el estado
-- de la fuente y la integridad referencial es responsabilidad de la catedra. Una
-- inconsistencia transitoria de la fuente no debe impedir aplicar una version.

CREATE TABLE professional_category (
    id          BIGINT        NOT NULL,
    name        VARCHAR(255)  NOT NULL,
    description VARCHAR(2000) NULL,
    enabled     BOOLEAN       NOT NULL,
    created_at  TIMESTAMPTZ   NULL,
    updated_at  TIMESTAMPTZ   NULL,
    CONSTRAINT pk_professional_category PRIMARY KEY (id)
);

CREATE TABLE professional (
    id          BIGINT       NOT NULL,
    category_id BIGINT       NOT NULL,
    first_name  VARCHAR(255) NOT NULL,
    last_name   VARCHAR(255) NOT NULL,
    enabled     BOOLEAN      NOT NULL,
    created_at  TIMESTAMPTZ  NULL,
    updated_at  TIMESTAMPTZ  NULL,
    CONSTRAINT pk_professional PRIMARY KEY (id)
);

CREATE INDEX ix_professional_category ON professional (category_id);

CREATE TABLE weekly_schedule (
    id                    BIGINT      NOT NULL,
    professional_id       BIGINT      NOT NULL,
    day_of_week           VARCHAR(10) NOT NULL,
    start_time            TIME        NOT NULL,
    end_time              TIME        NOT NULL,
    slot_duration_minutes INTEGER     NOT NULL,
    enabled               BOOLEAN     NOT NULL,
    created_at            TIMESTAMPTZ NULL,
    updated_at            TIMESTAMPTZ NULL,
    CONSTRAINT pk_weekly_schedule PRIMARY KEY (id),
    CONSTRAINT ck_weekly_schedule_day CHECK (day_of_week IN
        ('MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'))
);

CREATE INDEX ix_weekly_schedule_professional_day ON weekly_schedule (professional_id, day_of_week);

-- Eventos Kafka ya procesados: deduplicacion por eventId (clave de idempotencia del contrato).
-- duplicate_count hace observable cuantas veces llego repetido el mismo evento.
CREATE TABLE processed_event (
    event_id        VARCHAR(200) NOT NULL,
    event_type      VARCHAR(100) NOT NULL,
    processed_at    TIMESTAMPTZ  NOT NULL,
    duplicate_count INTEGER      NOT NULL DEFAULT 0,
    last_seen_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_processed_event PRIMARY KEY (event_id)
);

-- Datos adicionales del estado de sincronizacion.
ALTER TABLE sync_state ADD COLUMN remote_version BIGINT NULL;
ALTER TABLE sync_state ADD COLUMN last_full_sync_at TIMESTAMPTZ NULL;
