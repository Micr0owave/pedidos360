package cl.duoc.p360.notify.exception;

/** Error recuperable (ej: servicio externo caido). Se reintenta y, al agotar los reintentos, va a la DLQ. */
public class TransientProcessingException extends RuntimeException {
    public TransientProcessingException(String message) { super(message); }
}
