package com.carevoice.controller;

import com.carevoice.service.MonitoringFieldCatalogService;
import com.carevoice.service.MonitoringFieldCatalogService.CatalogField;
import com.carevoice.domain.MonitoringCategory;
import com.carevoice.domain.MonitoringFieldRuntimeSupport;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clinician/monitoring-fields")
public class ClinicianFieldCatalogController {
    private final MonitoringFieldCatalogService catalog;

    public ClinicianFieldCatalogController(MonitoringFieldCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping
    @PreAuthorize("hasRole('CLINICIAN')")
    public List<CatalogField> list(
            @RequestParam(required = false) MonitoringFieldRuntimeSupport runtimeSupport,
            @RequestParam(required = false) MonitoringCategory category,
            @RequestParam(required = false) Boolean planAskable) {
        return catalog.list(runtimeSupport, category, planAskable);
    }
}
