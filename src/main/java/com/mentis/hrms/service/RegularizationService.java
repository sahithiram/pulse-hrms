package com.mentis.hrms.service;

import com.mentis.hrms.model.Attendance;
import com.mentis.hrms.model.AttendanceRegularization;
import com.mentis.hrms.repository.AttendanceRegularizationRepository;
import com.mentis.hrms.repository.AttendanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

/** REQ-5-04/09 — regularization workflow with monthly limit; approval edits attendance + recomputes metrics. */
@Service
public class RegularizationService {

    private static final Logger logger = LoggerFactory.getLogger(RegularizationService.class);

    @Autowired private AttendanceRegularizationRepository regularizationRepository;
    @Autowired private AttendanceRepository attendanceRepository;
    @Autowired private DailyMetricsService dailyMetricsService;
    @Autowired private ApplicationEventPublisher eventPublisher;

    @Value("${hrms.regularization.monthly-limit:3}")
    private int monthlyLimit;

    @Transactional
    public AttendanceRegularization request(String employeeId, LocalDate date, String type,
                                            LocalDateTime requestedIn, LocalDateTime requestedOut,
                                            String reason, String attachment) {
        LocalDate from = date.with(TemporalAdjusters.firstDayOfMonth());
        LocalDate to = date.with(TemporalAdjusters.lastDayOfMonth());
        long used = regularizationRepository.countRequestsInRange(employeeId, from, to);
        if (used >= monthlyLimit) {
            throw new IllegalStateException("Monthly regularization limit reached (" + monthlyLimit + ")");
        }
        AttendanceRegularization r = new AttendanceRegularization();
        r.setEmployeeId(employeeId);
        r.setAttendanceDate(date);
        r.setType(type == null ? "WRONG_TIME" : type.trim().toUpperCase());
        r.setRequestedIn(requestedIn);
        r.setRequestedOut(requestedOut);
        r.setReason(reason);
        r.setAttachment(attachment);
        r.setStatus("PENDING");
        regularizationRepository.save(r);
        logger.info("Regularization {} requested by {} for {}", r.getId(), employeeId, date);
        return r;
    }

    @Transactional
    public AttendanceRegularization approve(Long id, String approver, String remarks) {
        AttendanceRegularization r = getOrThrow(id);
        if (!"PENDING".equalsIgnoreCase(r.getStatus())) {
            throw new IllegalStateException("Regularization already decided: " + r.getStatus());
        }
        // Apply onto attendance sessions (tagged REGULARIZED + edited_flag)
        List<Attendance> sessions = attendanceRepository.findByEmployeeIdAndAttendanceDate(r.getEmployeeId(), r.getAttendanceDate());
        Attendance target = sessions.isEmpty() ? new Attendance(r.getEmployeeId(), r.getAttendanceDate()) :
                sessions.stream().filter(a -> a.getCheckInTime() == null).findFirst().orElse(sessions.get(0));
        if (r.getRequestedIn() != null) {
            target.setCheckInTime(r.getRequestedIn());
        }
        if (r.getRequestedOut() != null) {
            target.setCheckOutTime(r.getRequestedOut());
        }
        target.setSource("REGULARIZED");
        target.setEditedFlag(true);
        attendanceRepository.save(target);

        r.setStatus("APPROVED");
        r.setApprover(approver);
        r.setActedAt(LocalDateTime.now());
        regularizationRepository.save(r);

        dailyMetricsService.recompute(r.getEmployeeId(), r.getAttendanceDate());
        eventPublisher.publishEvent(new RegularizationApprovedEvent(r.getId(), r.getEmployeeId(), r.getAttendanceDate(), approver));
        logger.info("Regularization {} approved by {}", id, approver);
        return r;
    }

    @Transactional
    public AttendanceRegularization reject(Long id, String approver, String remarks) {
        AttendanceRegularization r = getOrThrow(id);
        if (!"PENDING".equalsIgnoreCase(r.getStatus())) {
            throw new IllegalStateException("Regularization already decided: " + r.getStatus());
        }
        r.setStatus("REJECTED");
        r.setApprover(approver);
        r.setActedAt(LocalDateTime.now());
        regularizationRepository.save(r);
        return r;
    }

    public List<AttendanceRegularization> pending() {
        return regularizationRepository.findByStatusOrderByIdDesc("PENDING");
    }

    public List<AttendanceRegularization> mine(String employeeId) {
        return regularizationRepository.findByEmployeeIdOrderByIdDesc(employeeId);
    }

    private AttendanceRegularization getOrThrow(Long id) {
        return regularizationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Regularization not found: " + id));
    }
}
