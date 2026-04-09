package de.schneefisch.stepup.exception;

import de.schneefisch.stepup.acr.InsufficientAcrException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientAcrException.class)
    public ResponseEntity<Map<String, String>> handleInsufficientAcr(InsufficientAcrException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header("WWW-Authenticate",
                        "Bearer error=\"insufficient_authentication_level\", acr_values=\"mfa\"")
                .body(Map.of(
                        "error", "insufficient_authentication_level",
                        "error_description", ex.getMessage(),
                        "acr_values", "mfa"
                ));
    }
}
