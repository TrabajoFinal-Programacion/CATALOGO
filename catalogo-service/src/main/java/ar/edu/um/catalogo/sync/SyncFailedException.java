package ar.edu.um.catalogo.sync;

/** La sincronizacion no pudo completarse. El estado local quedo intacto en su ultima version completa. */
public class SyncFailedException extends RuntimeException {

    public SyncFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
