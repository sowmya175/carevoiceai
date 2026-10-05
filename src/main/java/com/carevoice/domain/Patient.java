package com.carevoice.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "patients")
public class Patient {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String displayName;

    @Column(nullable = false)
    private String monitoringPlan;

    protected Patient() {}

    public Patient(String displayName, String monitoringPlan) {
        this.displayName = displayName;
        this.monitoringPlan = monitoringPlan;
    }

    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getMonitoringPlan() { return monitoringPlan; }
}
