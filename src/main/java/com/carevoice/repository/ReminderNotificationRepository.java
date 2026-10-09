package com.carevoice.repository;
import com.carevoice.domain.ReminderNotification;
import com.carevoice.domain.ReminderStatus;
import com.carevoice.domain.ReminderType;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReminderNotificationRepository extends JpaRepository<ReminderNotification, Long> {
    Optional<ReminderNotification> findByPatient_IdAndReminderDateAndReminderType(
            Long patientId,
            LocalDate reminderDate,
            ReminderType reminderType
    );

    Optional<ReminderNotification> findByIdAndPatient_Id(Long id, Long patientId);

    List<ReminderNotification> findByPatient_IdAndStatusOrderByCreatedAtDescIdDesc(
            Long patientId,
            ReminderStatus status,
            Pageable pageable
    );
}
