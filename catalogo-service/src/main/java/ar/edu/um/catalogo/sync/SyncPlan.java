package ar.edu.um.catalogo.sync;

/** Que hacer para llevar la copia local a la version publicada. */
public record SyncPlan(Type type, long fromVersion, long toVersion, String reason) {

    public enum Type {
        /** La copia local ya esta en la version publicada. */
        NONE,
        /** Aplicar changes:{fromVersion} .. changes:{toVersion} en orden. */
        INCREMENTAL,
        /** Reconstruir desde el snapshot REST. */
        FULL
    }

    public static SyncPlan none(long version) {
        return new SyncPlan(Type.NONE, version, version, "AL_DIA");
    }

    public static SyncPlan incremental(long from, long to) {
        return new SyncPlan(Type.INCREMENTAL, from, to, "INCREMENTAL");
    }

    public static SyncPlan full(String reason) {
        return new SyncPlan(Type.FULL, 0, 0, reason);
    }
}
