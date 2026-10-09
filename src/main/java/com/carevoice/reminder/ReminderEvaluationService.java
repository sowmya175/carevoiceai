package com.carevoice.reminder;

import com.carevoice.checkin.DailyCheckInService;
import com.carevoice.checkin.DailyCheckInStatus;
import com.carevoice.checkin.DailyCheckInStatusResponse;
import com.carevoice.checkin.PatientLocalDateService;
import com.carevoice.config.ReminderProperties;
import com.carevoice.domain.Patient;
import com.carevoice.repository.PatientRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * One scan evaluates every enabled preference. It never starts a monitoring session.
 *
 * <p>Patient.timezone is authoritative. Historical {@code reminderDate} values are not rewritten when
 * the timezone changes. If the new local date already has a daily reminder, another one is not sent.
 * A local date that does not yet have a row can still receive one while the patient is inside the due window.
 * The due window does not cross midnight, so a missed morning reminder is not sent that evening.
 */
@Service
public class ReminderEvaluationService {
    private static final Logger log = LoggerFactory.getLogger(ReminderEvaluationService.class);

    private final PatientReminderPreferenceRepository preferences;
    private final ReminderNotificationRepository notifications;
    private final PatientRepository patients;
    private final PatientLocalDateService dates;
    private final DailyCheckInService dailyCheckIns;
    private final NotificationService delivery;
    private final ReminderProperties properties;
    private final PlatformTransactionManager transactions;
    private final Clock clock;

    public ReminderEvaluationService(
            PatientReminderPreferenceRepository preferences,
            ReminderNotificationRepository notifications,
            PatientRepository patients,
            PatientLocalDateService dates,
            DailyCheckInService dailyCheckIns,
            NotificationService delivery,
            ReminderProperties properties,
            PlatformTransactionManager transactions,
            Clock clock) {
        this.preferences = preferences;
        this.notifications = notifications;
        this.patients = patients;
        this.dates = dates;
        this.dailyCheckIns = dailyCheckIns;
        this.delivery = delivery;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    public void evaluateDueReminders() {
        for (Long patientId : preferences.findEnabledPatientIds()) {
            try {
                evaluatePatient(patientId);
            } catch (RuntimeException ex) {
                log.warn("reminder evaluation failed patientId={} errorType={}",
                        patientId, ex.getClass().getSimpleName());
            }
        }
    }

    public void evaluatePatient(Long patientId) {
        Optional<PatientReminderPreference> preference = preferences.findByPatient_Id(patientId);
        if (preference.isEmpty() || !preference.get().isEnabled()) {
            return;
        }
        Patient patient = patients.findById(patientId).orElse(null);
        if (patient == null) {
            return;
        }
        LocalDate reminderDate = dates.today(patient);
        LocalTime localTime = dates.localTime(patient);
        if (!ReminderWindow.isDue(localTime, preference.get().getReminderTime(), properties.getWindowMinutes())) {
            return;
        }
        if (notifications.findByPatient_IdAndReminderDateAndReminderType(
                patientId, reminderDate, ReminderType.DAILY_CHECK_IN).isPresent()) {
            return;
        }
        Claim claim;
        try {
            claim = claim(patientId, reminderDate).orElse(null);
        } catch (RuntimeException ex) {
            if (isDuplicate(ex)) {
                return;
            }
            throw ex;
        }
        if (claim == null) {
            return;
        }
        try {
            delivery.deliver(claim.notificationId());
            log.info("reminder delivered patientId={} reminderDate={} channel={} status=DELIVERED",
                    claim.patientId(), claim.reminderDate(), ReminderChannel.IN_APP);
        } catch (RuntimeException ex) {
            markFailed(claim.notificationId(), ex.getClass().getSimpleName());
            log.warn("reminder delivery failed patientId={} reminderDate={} channel={} status=FAILED errorType={}",
                    claim.patientId(), claim.reminderDate(), ReminderChannel.IN_APP, ex.getClass().getSimpleName());
        }
    }

    private Optional<Claim> claim(Long patientId, LocalDate reminderDate) {
        TransactionTemplate transaction = requiresNew();
        Optional<Claim> claimed = transaction.execute(status -> {
            PatientReminderPreference preference = preferences.lockByPatientId(patientId).orElse(null);
            if (preference == null || !preference.isEnabled()) {
                return Optional.<Claim>empty();
            }
            Patient patient = preference.getPatient();
            LocalDate today = dates.today(patient);
            if (!today.equals(reminderDate)
                    || !ReminderWindow.isDue(dates.localTime(patient), preference.getReminderTime(), properties.getWindowMinutes())) {
                return Optional.<Claim>empty();
            }
            if (notifications.findByPatient_IdAndReminderDateAndReminderType(
                    patientId, today, ReminderType.DAILY_CHECK_IN).isPresent()) {
                return Optional.<Claim>empty();
            }
            ReminderTemplate template = templateFor(dailyCheckIns.status(patientId));
            if (template == null) {
                return Optional.<Claim>empty();
            }
            ReminderNotification notification = notifications.saveAndFlush(ReminderNotification.pending(
                    patient, today, template, OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC)));
            return Optional.of(new Claim(notification.getId(), patientId, today));
        });
        return claimed == null ? Optional.empty() : claimed;
    }

    private void markFailed(Long notificationId, String failureType) {
        try {
            requiresNew().executeWithoutResult(status -> notifications.findById(notificationId).ifPresent(notification -> {
                if (notification.getStatus() == ReminderStatus.DELIVERED) {
                    return;
                }
                notification.markFailed(failureType);
                notifications.save(notification);
            }));
        } catch (RuntimeException ex) {
            log.warn("reminder failure status could not be saved notificationId={} channel={} status=FAILED errorType={}",
                    notificationId, ReminderChannel.IN_APP, ex.getClass().getSimpleName());
        }
    }

    private TransactionTemplate requiresNew() {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction;
    }

    private static ReminderTemplate templateFor(DailyCheckInStatusResponse daily) {
        if (daily.previousDaySession()) {
            return ReminderTemplate.PREVIOUS_CHECK_IN_CONTINUE;
        }
        if (daily.status() == DailyCheckInStatus.NOT_STARTED) {
            return ReminderTemplate.DAILY_CHECK_IN_START;
        }
        return null;
    }

    private static boolean isDuplicate(Throwable error) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current instanceof DataIntegrityViolationException) {
                return true;
            }
        }
        return false;
    }

    private record Claim(Long notificationId, Long patientId, LocalDate reminderDate) {}
}
