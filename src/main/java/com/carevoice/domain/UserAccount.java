package com.carevoice.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "user_accounts")
public class UserAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true, length = 32)
    private String username;
    /** Legacy column from email login. Unused for authentication and not returned by the API. */
    @JsonIgnore @Column(length = 254)
    private String email;
    @JsonIgnore @Column(nullable = false, length = 100)
    private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20)
    private AccountRole role;
    @Column(nullable = false)
    private boolean enabled = true;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id", unique = true)
    private Patient patient;
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = createdAt;

    protected UserAccount() {}
    public UserAccount(String username, String passwordHash, AccountRole role, Patient patient) {
        this.username = username; this.passwordHash = passwordHash; this.role = role; this.patient = patient;
    }
    @PreUpdate void touch() { updatedAt = Instant.now(); }
    public Long getId() { return id; }
    public String getUsername() { return username; }
    @JsonIgnore public String getPasswordHash() { return passwordHash; }
    public AccountRole getRole() { return role; }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public Patient getPatient() { return patient; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    @Override public String toString() { return "UserAccount[id=" + id + ",username=" + username + ",role=" + role + "]"; }
}
