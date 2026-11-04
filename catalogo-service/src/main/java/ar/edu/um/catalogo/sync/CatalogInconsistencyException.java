package ar.edu.um.catalogo.sync;

/**
 * Los datos publicados no permiten continuar de forma segura con la sincronizacion incremental:
 * falta un changes:{version}, falta una entidad referenciada, un JSON es invalido, etc.
 * La respuesta a esta excepcion es una sincronizacion completa (snapshot).
 */
public class CatalogInconsistencyException extends RuntimeException {

    public CatalogInconsistencyException(String message) {
        super(message);
    }

    public CatalogInconsistencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
