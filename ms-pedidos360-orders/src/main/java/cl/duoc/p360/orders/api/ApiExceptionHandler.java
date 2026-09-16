package cl.duoc.p360.orders.api;

import cl.duoc.p360.orders.domain.InvalidTransitionException;
import cl.duoc.p360.orders.domain.OrderNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidTransitionException.class)
    public ResponseEntity<Dtos.ErrorResponse> onInvalidTransition(InvalidTransitionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new Dtos.ErrorResponse("TRANSICION_INVALIDA", ex.getMessage(),
                        Map.of("from", ex.getFrom(), "to", ex.getTo())));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<Dtos.ErrorResponse> onNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new Dtos.ErrorResponse("NO_ENCONTRADO", ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Dtos.ErrorResponse> onValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
          .forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(new Dtos.ErrorResponse("VALIDACION", "Revisa los campos enviados", fields));
    }
}
