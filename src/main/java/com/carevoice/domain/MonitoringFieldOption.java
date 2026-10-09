package com.carevoice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "monitoring_field_options",
        uniqueConstraints = @UniqueConstraint(columnNames = {"field_definition_id", "code"})
)
public class MonitoringFieldOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "field_definition_id", nullable = false)
    private MonitoringFieldDefinition fieldDefinition;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 120)
    private String displayLabel;

    @Column(nullable = false)
    private int displayOrder;

    @Column(nullable = false)
    private boolean active = true;

    protected MonitoringFieldOption() {}

    public MonitoringFieldOption(MonitoringFieldDefinition fieldDefinition, String code, String displayLabel, int displayOrder) {
        this.fieldDefinition = fieldDefinition;
        this.code = code;
        this.displayLabel = displayLabel;
        this.displayOrder = displayOrder;
        this.active = true;
    }

    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getDisplayLabel() { return displayLabel; }
    public int getDisplayOrder() { return displayOrder; }
    public boolean isActive() { return active; }
}
