package com.carevoice.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "monitoring_field_definitions")
public class MonitoringFieldDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(nullable = false, length = 160)
    private String displayName;

    @Column(nullable = false, length = 500)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MonitoringAnswerType answerType;

    @Column(length = 32)
    private String unit;

    private Double minimumValue;
    private Double maximumValue;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private MonitoringFieldRuntimeSupport runtimeSupport;

    @Enumerated(EnumType.STRING)
    @Column(unique = true, length = 40)
    private MonitoringField legacyField;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "monitoring_field_categories", joinColumns = @JoinColumn(name = "field_definition_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 40)
    private Set<MonitoringCategory> categories = new LinkedHashSet<>();

    @OneToMany(mappedBy = "fieldDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder asc")
    private List<MonitoringFieldOption> options = new ArrayList<>();

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = createdAt;

    protected MonitoringFieldDefinition() {}

    public MonitoringFieldDefinition(
            String code,
            String displayName,
            String description,
            MonitoringAnswerType answerType,
            String unit,
            Double minimumValue,
            Double maximumValue,
            MonitoringFieldRuntimeSupport runtimeSupport,
            MonitoringField legacyField,
            Set<MonitoringCategory> categories) {
        this.code = code;
        this.displayName = displayName;
        this.description = description;
        this.answerType = answerType;
        this.unit = unit;
        this.minimumValue = minimumValue;
        this.maximumValue = maximumValue;
        this.active = true;
        this.runtimeSupport = runtimeSupport;
        this.legacyField = legacyField;
        this.categories = new LinkedHashSet<>(categories);
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public void addOption(String code, String displayLabel, int displayOrder) {
        options.add(new MonitoringFieldOption(this, code, displayLabel, displayOrder));
    }

    @PreUpdate
    void touch() { updatedAt = Instant.now(); }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getDisplayName() { return displayName; }
    public String getDescription() { return description; }
    public MonitoringAnswerType getAnswerType() { return answerType; }
    public String getUnit() { return unit; }
    public Double getMinimumValue() { return minimumValue; }
    public Double getMaximumValue() { return maximumValue; }
    public boolean isActive() { return active; }
    public MonitoringFieldRuntimeSupport getRuntimeSupport() { return runtimeSupport; }
    public MonitoringField getLegacyField() { return legacyField; }
    public Set<MonitoringCategory> getCategories() { return categories; }
    public List<MonitoringFieldOption> getOptions() { return options; }
}
