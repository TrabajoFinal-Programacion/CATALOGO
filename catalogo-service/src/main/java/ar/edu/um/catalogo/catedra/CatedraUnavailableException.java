package ar.edu.um.catalogo.catedra;

/** La API de la catedra no respondio o respondio con error luego de los reintentos permitidos. */
public class CatedraUnavailableException extends RuntimeException {

    public CatedraUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
