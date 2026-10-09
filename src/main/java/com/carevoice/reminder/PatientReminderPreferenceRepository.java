package com.carevoice.reminder;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface PatientReminderPreferenceRepository extends JpaRepository<PatientReminderPreference, Long> {
    Optional<PatientReminderPreference> findByPatient_Id(Long patientId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select preference from PatientReminderPreference preference where preference.patient.id = :patientId")
    Optional<PatientReminderPreference> lockByPatientId(Long patientId);

    @Query("select preference.patient.id from PatientReminderPreference preference where preference.enabled = true")
    List<Long> findEnabledPatientIds();
}
