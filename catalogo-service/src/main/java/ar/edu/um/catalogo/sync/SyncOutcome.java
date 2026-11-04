package ar.edu.um.catalogo.sync;

/** Resultado de una ejecucion de sincronizacion. */
public record SyncOutcome(SyncPlan.Type type, Long previousVersion, Long currentVersion, String reason) {}
