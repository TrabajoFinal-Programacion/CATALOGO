-- Estado de la copia local del catalogo. Una sola fila (id = 1).
-- local_version es la version de catalogo cuyos datos estan aplicados en esta base;
-- NULL significa base vacia (todavia no se aplico ningun snapshot).

CREATE TABLE sync_state (
    id            SMALLINT     NOT NULL,
    local_version BIGINT       NULL,
    status        VARCHAR(30)  NOT NULL,
    last_sync_at  TIMESTAMPTZ  NULL,
    last_error    VARCHAR(1000) NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_sync_state PRIMARY KEY (id),
    CONSTRAINT ck_sync_state_singleton CHECK (id = 1)
);

INSERT INTO sync_state (id, local_version, status, last_sync_at, last_error, updated_at)
VALUES (1, NULL, 'EMPTY', NULL, NULL, now());
