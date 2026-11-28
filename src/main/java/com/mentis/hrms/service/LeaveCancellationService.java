package com.mentis.hrms.service;

import com.mentis.hrms.model.LeaveCancellationRequest;
import com.mentis.hrms.model.LeaveRequest;
import com.mentis.hrms.model.LeaveRequestDay;
import com.mentis.hrms.model.LeaveType;
import com.mentis.hrms.model.enums.LeaveEntryType;
import com.mentis.hrms.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * WAS A 0-BYTE STUB — now implemented (REQ-4-07, REQ-4-11).
 * Flow: employee asks to cancel (whole request or partial future days) → manager/HR approves →
 * unused days are credited back to the ledger (CREDIT_REVERSAL) and leave_request_day rows are un-counted.
 * Cancelling days already started releases only the days that have NOT yet passed.
 */
@Service
public class LeaveCancellationService {

    private static final Logger logger = LoggerFactory.getLogger(LeaveCancellationService.class);

    @Autowired private LeaveCancellationRequestRepository cancellationRepository;
    @Autowired private LeaveRequestRepository leaveRequestRepository;
    @Autowired private LeaveRequestDayRepository leaveRequestDayRepository;
    @Autowired private LeaveTypeRepository leaveTypeRepository;
    @Autowired private LeaveLedgerService ledgerService;
    @Autowired private ApplicationEventPublisher eventPublisher;

    /** Employee-side: request cancellation of a future/ongoing approved leave. partialDaysISO = null → whole request. */
    @Transactional
    public LeaveCancellationRequest requestCancellation(String leaveRequestId, String requestedBy,
                                                        String reason, List<LocalDate> partialDaysISO) {
        LeaveRequest request = leaveRequestRepository.findById(leaveRequestId)
                .orElseThrow(() -> new IllegalArgumentException("Leave request not found: " + leaveRequestId));
        if (!"APPROVED".equalsIgnoreCase(request.getStatus()) && !"CANCELLATION_PENDING".equalsIgnoreCase(request.getStatus())) {
            throw new IllegalStateException("Only APPROVED leaves can be cancelled (current status: " + request.getStatus() + ")");
        }
        LocalDate today = LocalDate.now();
        List<LocalDate> days = resolveCancellationDays(request, partialDaysISO, today);
        if (days.isEmpty()) {
            throw new IllegalStateException("Nothing left to cancel: all days have already passed");
        }
        LeaveCancellationRequest cr = new LeaveCancellationRequest();
        cr.setLeaveRequestId(leaveRequestId);
        cr.setRequestedBy(requestedBy);
        cr.setReason(reason);
        cr.setPartialDateList(days);
        cr.setStatus("PENDING");
        cancellationRepository.save(cr);

        request.setStatus("CANCELLATION_PENDING");
        leaveRequestRepository.save(request);
        logger.info("Cancellation requested for leave {} by {} ({} day(s))", leaveRequestId, requestedBy, days.size());
        return cr;
    }

    /** Approver-side (manager/HR — approver identity must be validated by caller layer via Sprint-2 policy). */
    @Transactional
    public LeaveCancellationRequest approve(Long cancellationId, String approverId, String remarks) {
        LeaveCancellationRequest cr = getOrThrow(cancellationId);
        if (!"PENDING".equalsIgnoreCase(cr.getStatus())) {
            throw new IllegalStateException("Cancellation already decided: " + cr.getStatus());
        }
        LeaveRequest request = leaveRequestRepository.findById(cr.getLeaveRequestId())
                .orElseThrow(() -> new IllegalStateException("Leave request missing: " + cr.getLeaveRequestId()));
        List<LocalDate> days = cr.partialDateList();
        if (days.isEmpty()) {
            days = leaveRequestDayRepository.findByRequestId(request.getId()).stream()
                    .map(LeaveRequestDay::getDay)
                    .sorted()
                    .collect(Collectors.toList());
        }
        cr.setStatus("APPROVED");
        cr.setApproverId(approverId);
        cr.setActedAt(LocalDateTime.now());
        cancellationRepository.save(cr);

        // Un-count cancelled days and credit them back
        BigDecimal released = BigDecimal.ZERO;
        Map<LocalDate, LeaveRequestDay> dayRows = leaveRequestDayRepository.findByRequestId(request.getId())
                .stream().collect(Collectors.toMap(LeaveRequestDay::getDay, d -> d, (a, b) -> a));
        for (LocalDate day : days) {
            LeaveRequestDay row = dayRows.get(day);
            if (row != null && row.isCounted()) {
                row.setCounted(false);
                leaveRequestDayRepository.save(row);
                released = released.add(row.getDayFraction());
            }
        }

        Long leaveTypeId = request.getLeaveTypeId();
        if (leaveTypeId == null) {
            // legacy rows without leave_type_id: match by free-text code
            leaveTypeId = leaveTypeRepository.findByCode(String.valueOf(request.getLeaveType()).toUpperCase())
                    .map(LeaveType::getId)
                    .orElse(null);
        }
        if (leaveTypeId != null && released.signum() > 0) {
            ledgerService.reverseDebit(request.getEmployeeId(), leaveTypeId, released,
                    LocalDate.now(), "cancellation", String.valueOf(cr.getId()),
                    "Leave cancellation approved by " + approverId, approverId);
        } else if (leaveTypeId == null) {
            logger.warn("Could not resolve leave_type_id for request {} — ledger reversal SKIPPED, balance not credited back", request.getId());
        }

        List<LeaveRequestDay> remainingCounted = leaveRequestDayRepository.findByRequestId(request.getId())
                .stream().filter(LeaveRequestDay::isCounted).collect(Collectors.toList());
        request.setStatus(remainingCounted.isEmpty() ? "CANCELLED" : "APPROVED");
        if (remainingCounted.isEmpty()) {
            request.setCancelReason(cr.getReason());
        }
        // keep totalDays in sync for legacy readers
        request.setTotalDays(remainingCounted.stream().mapToDouble(d -> d.getDayFraction().doubleValue()).sum());
        leaveRequestRepository.save(request);

        eventPublisher.publishEvent(new LeaveCancelledEvent(request.getId(), request.getEmployeeId(),
                days.size() != dayRows.size(), days));
        logger.info("Cancellation {} approved by {} — released {} day(s)", cr.getId(), approverId, released);
        return cr;
    }

    @Transactional
    public LeaveCancellationRequest reject(Long cancellationId, String approverId, String remarks) {
        LeaveCancellationRequest cr = getOrThrow(cancellationId);
        if (!"PENDING".equalsIgnoreCase(cr.getStatus())) {
            throw new IllegalStateException("Cancellation already decided: " + cr.getStatus());
        }
        cr.setStatus("REJECTED");
        cr.setApproverId(approverId);
        cr.setActedAt(LocalDateTime.now());
        cancellationRepository.save(cr);

        // roll the leave back to APPROVED if it is still CANCELLATION_PENDING and no other pending cancellation exists
        leaveRequestRepository.findById(cr.getLeaveRequestId()).ifPresent(r -> {
            boolean otherPending = cancellationRepository.findByLeaveRequestIdOrderByIdDesc(r.getId())
                    .stream().anyMatch(x -> x.getId() != null && !x.getId().equals(cr.getId())
                            && "PENDING".equalsIgnoreCase(x.getStatus()));
            if ("CANCELLATION_PENDING".equalsIgnoreCase(r.getStatus()) && !otherPending) {
                r.setStatus("APPROVED");
                leaveRequestRepository.save(r);
            }
        });
        logger.info("Cancellation {} rejected by {}", cancellationId, approverId);
        return cr;
    }

    public List<LeaveCancellationRequest> pending() {
        return cancellationRepository.findByStatusOrderByIdDesc("PENDING");
    }

    public List<LeaveCancellationRequest> myRequests(String employeeId) {
        // cancellation rows keyed by request → filter by request owner is done at caller (needs join) or via request lookup
        return cancellationRepository.findByRequestedByOrderByIdDesc(employeeId);
    }

    // ---- helpers ----

    private LeaveCancellationRequest getOrThrow(Long id) {
        return cancellationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Cancellation request not found: " + id));
    }

    private List<LocalDate> resolveCancellationDays(LeaveRequest request, List<LocalDate> requested, LocalDate today) {
        LocalDate start = request.getStartDate();
        LocalDate end = request.getEndDate();
        Set<LocalDate> range = new TreeSet<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!d.isBefore(today)) range.add(d);   // only today/future releasable
        }
        if (requested == null || requested.isEmpty()) return new ArrayList<>(range);
        Set<LocalDate> inRange = new TreeSet<>();
        for (LocalDate d : requested) {
            if (d != null && range.contains(d)) inRange.add(d);
        }
        return new ArrayList<>(inRange);
    }
}
