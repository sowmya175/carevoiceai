package com.carevoice.repository;

import com.carevoice.domain.Patient;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Patient p where p.id = :id")
    Optional<Patient> findByIdForUpdate(@Param("id") Long id);
}
