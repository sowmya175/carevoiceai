package com.carevoice.repository;
import com.carevoice.domain.UserAccount;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {
    @EntityGraph(attributePaths = "patient")
    Optional<UserAccount> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByPatient_Id(Long patientId);
}
