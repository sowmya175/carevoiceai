package com.carevoice.exception;
import com.carevoice.controller.AuthController;

import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = AuthController.class)
@Order(-1)
public class AuthExceptionHandler {
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<?> credentials() { return error(HttpStatus.UNAUTHORIZED, "Invalid username or password."); }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<?> validation() { return error(HttpStatus.BAD_REQUEST, "Check the username, required fields, and password length (12–72 characters)."); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<?> duplicate() { return error(HttpStatus.CONFLICT, "That username is already taken."); }
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<?> status(ResponseStatusException exception) { return error(exception.getStatusCode(), exception.getReason()); }
    private ResponseEntity<?> error(HttpStatusCode status, String message) { return ResponseEntity.status(status).body(Map.of("error", message)); }
}
