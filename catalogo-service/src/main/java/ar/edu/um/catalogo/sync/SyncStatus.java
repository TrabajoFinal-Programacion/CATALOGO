package ar.edu.um.catalogo.sync;

/** Estado observable de la copia local del catalogo. */
public enum SyncStatus {
    /** Base vacia: nunca se aplico un snapshot. */
    EMPTY,
    /** La copia local esta aplicada y al dia segun la ultima verificacion. */
    UP_TO_DATE,
    /** Hay una sincronizacion en curso. */
    SYNCING,
    /** La ultima sincronizacion fallo; la copia local sigue siendo la de local_version. */
    ERROR
}
