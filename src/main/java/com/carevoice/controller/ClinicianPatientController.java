package com.carevoice.controller;

import com.carevoice.dto.clinician.ClinicianPatientDaily;
import com.carevoice.service.DailyCheckInService;
import com.carevoice.service.ClinicianReviewService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clinician/patients")
public class ClinicianPatientController {
    private final DailyCheckInService dailyCheckIns;
    private final ClinicianReviewService reviews;

    public ClinicianPatientController(DailyCheckInService dailyCheckIns, ClinicianReviewService reviews) {
        this.dailyCheckIns = dailyCheckIns;
        this.reviews = reviews;
    }

    @GetMapping
    @PreAuthorize("hasRole('CLINICIAN')")
    public List<ClinicianPatientDaily> list() {
        return dailyCheckIns.clinicianPatients();
    }

    @GetMapping("/{patientId}")
    @PreAuthorize("hasRole('CLINICIAN')")
    public ClinicianReviewService.ClinicianPatientOverview patient(@PathVariable Long patientId) {
        return reviews.patient(patientId);
    }
}
