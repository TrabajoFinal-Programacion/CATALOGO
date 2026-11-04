package ar.edu.um.catalogo.sync;

/** Versiones publicadas en Redis: la actual y la mas antigua cuyo changes:{v} sigue disponible. */
public record RemoteVersions(long current, long oldestAvailable) {}
