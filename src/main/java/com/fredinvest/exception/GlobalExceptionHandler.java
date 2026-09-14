package com.fredinvest.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<?> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", HttpStatus.UNAUTHORIZED.value(),
                "error", "Unauthorized",
                "message", "E-mail ou senha incorretos. Se você se cadastrou pelo Google, utilize o botão 'Entrar com Google'."
        ));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> handleAuthException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", HttpStatus.UNAUTHORIZED.value(),
                "error", "Unauthorized",
                "message", ex.getMessage() != null ? ex.getMessage() : "Autenticação falhou."
        ));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Bad Request",
                "message", ex.getMessage() != null ? ex.getMessage() : "Requisição inválida."
        ));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidation(MethodArgumentNotValidException ex) {
        String errorMsg = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .findFirst()
                .orElse("Dados de entrada inválidos.");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", HttpStatus.BAD_REQUEST.value(),
                "error", "Bad Request",
                "message", errorMsg
        ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGeneral(Exception ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of(
                "timestamp", LocalDateTime.now().toString(),
                "status", HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "error", "Internal Server Error",
                "message", ex.getMessage() != null ? ex.getMessage() : "Erro interno no servidor."
        ));
    }
}

