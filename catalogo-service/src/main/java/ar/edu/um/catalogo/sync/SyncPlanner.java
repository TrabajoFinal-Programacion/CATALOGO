package ar.edu.um.catalogo.sync;

/**
 * Decide la estrategia de sincronizacion. Es una funcion pura (sin I/O) para poder probar
 * cada caso del contrato de forma aislada.
 *
 * Reglas (contrato 14.2 y 18.2):
 * - base vacia                         -> snapshot
 * - local mayor que la publicada       -> snapshot (estado local que no puede verificarse)
 * - local igual a la publicada         -> nada
 * - local menor que oldest-available   -> snapshot (faltan incrementos; con local=3 y oldest=4
 *                                         no se empieza en changes:4)
 * - en otro caso                       -> aplicar local+1 .. current en orden
 */
public final class SyncPlanner {

    private SyncPlanner() {}

    public static SyncPlan plan(Long localVersion, RemoteVersions remote) {
        if (localVersion == null) {
            return SyncPlan.full("BASE_VACIA");
        }
        long local = localVersion;
        if (local > remote.current()) {
            return SyncPlan.full("VERSION_LOCAL_ADELANTADA");
        }
        if (local == remote.current()) {
            return SyncPlan.none(local);
        }
        if (local < remote.oldestAvailable()) {
            return SyncPlan.full("FUERA_DE_VENTANA");
        }
        return SyncPlan.incremental(local + 1, remote.current());
    }
}
