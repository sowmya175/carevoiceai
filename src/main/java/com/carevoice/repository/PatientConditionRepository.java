package com.carevoice.repository;

import com.carevoice.domain.PatientCondition;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PatientConditionRepository extends JpaRepository<PatientCondition, Long> {
    boolean existsByPatient_Id(Long patientId);

    List<PatientCondition> findByPatient_IdOrderByPrimaryConditionDescConditionNameAsc(Long patientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PatientCondition c where c.patient.id = :patientId")
    List<PatientCondition> lockByPatientId(@Param("patientId") Long patientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from PatientCondition c where c.id = :id and c.patient.id = :patientId")
    Optional<PatientCondition> lockByIdAndPatientId(@Param("id") Long id, @Param("patientId") Long patientId);
}
