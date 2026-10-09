package com.carevoice.api;

import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice(assignableTypes = {
        ClinicianConditionController.class,
        PatientConditionController.class,
        ClinicianFieldCatalogController.class,
        ClinicianPlanProposalController.class
})
@Order(-1)
public class ConditionExceptionHandler {
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException exception) {
        String message = exception.getReason() == null ? "Request could not be completed." : exception.getReason();
        return ResponseEntity.status(exception.getStatusCode()).body(Map.of("error", message));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> filter(MethodArgumentTypeMismatchException exception) {
        return ResponseEntity.status(HttpStatusCode.valueOf(400)).body(Map.of("error", "Choose a valid catalog filter."));
    }
}
