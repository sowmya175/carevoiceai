package com.carevoice.api;

import com.carevoice.clinician.ClinicianReviewService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clinician/sessions")
public class ClinicianSessionController {
    private final ClinicianReviewService reviews;

    public ClinicianSessionController(ClinicianReviewService reviews) {
        this.reviews = reviews;
    }

    @GetMapping("/{sessionId}")
    @PreAuthorize("hasRole('CLINICIAN')")
    public ClinicianReviewService.ClinicianSessionDetail session(@PathVariable Long sessionId) {
        return reviews.session(sessionId);
    }
}
