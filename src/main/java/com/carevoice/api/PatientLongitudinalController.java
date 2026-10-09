package com.carevoice.api;

import org.springframework.security.access.prepost.PreAuthorize;

import com.carevoice.longitudinal.LongitudinalAnalysisService;
import com.carevoice.longitudinal.PatientLongitudinalResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.http.HttpStatus;

import java.util.Map;

@RestController
public class PatientLongitudinalController {
    private final LongitudinalAnalysisService analysis;

    public PatientLongitudinalController(LongitudinalAnalysisService analysis) {
        this.analysis = analysis;
    }

    @GetMapping("/api/patients/{patientId}/longitudinal-summary")
    @PreAuthorize("@patientAccess.canReadPatient(#patientId, authentication)")
    public PatientLongitudinalResponse summary(@PathVariable Long patientId,
                                               @RequestParam(defaultValue = "" + LongitudinalAnalysisService.DEFAULT_LIMIT) int limit) {
        return analysis.summarize(patientId, limit);
    }

    // The shared advice maps IllegalArgumentException (including nested number-format failures) to 404.
    // Keep malformed parameters on this endpoint a 400 without changing other endpoints' behavior.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidParameter(MethodArgumentTypeMismatchException exception) {
        return Map.of("error", "Invalid " + exception.getName());
    }
}
