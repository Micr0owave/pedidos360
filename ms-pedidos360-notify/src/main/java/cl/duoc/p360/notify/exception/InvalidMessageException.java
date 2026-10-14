package cl.duoc.p360.notify.exception;

/** Error NO recuperable: el mensaje esta mal formado o pide un fallo forzado. Reintentar no sirve; va directo a la DLQ. */
public class InvalidMessageException extends RuntimeException {
    public InvalidMessageException(String message) { super(message); }
}
