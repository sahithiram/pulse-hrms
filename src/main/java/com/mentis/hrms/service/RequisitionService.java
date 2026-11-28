package com.mentis.hrms.service;

import com.mentis.hrms.model.JobRequisitionLink;
import com.mentis.hrms.model.Requisition;
import com.mentis.hrms.repository.HrmsSeqRepository;
import com.mentis.hrms.repository.JobRequisitionLinkRepository;
import com.mentis.hrms.repository.RequisitionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * REQ-7-01 — requisition lifecycle. OPEN is the only state in which a linked Job may publish.
 * Requisition must be created/approved BEFORE its job goes live (JobPublishGuard).
 */
@Service
public class RequisitionService {

    @Autowired private RequisitionRepository requisitionRepository;
    @Autowired private JobRequisitionLinkRepository linkRepository;
    @Autowired private HrmsSeqRepository seqRepository;

    @Transactional
    public Requisition create(Requisition draft, String createdBy) {
        if (draft.getTitle() == null || draft.getTitle().isBlank()) {
            throw new IllegalArgumentException("Requisition title is required");
        }
        if (draft.getHeadcount() < 1) draft.setHeadcount(1);
        draft.setStatus(Requisition.DRAFT);
        draft.setReqCode(String.format("RQ-%d-%06d", LocalDate.now().getYear(), seqRepository.next("REQUISITION")));
        draft.setCreatedBy(createdBy);
        // createdAt defaults to LocalDateTime.now() in the model; id is @GeneratedValue(IDENTITY)
        return requisitionRepository.save(draft);
    }

    @Transactional
    public Requisition submitForApproval(Long requisitionId) {
        Requisition r = mustGet(requisitionId);
        require(r.getStatus(), Requisition.DRAFT, "submit");
        r.setStatus(Requisition.PENDING_APPROVAL);
        return requisitionRepository.save(r);
    }

    @Transactional
    public Requisition approve(Long requisitionId, String approver) {
        Requisition r = mustGet(requisitionId);
        require(r.getStatus(), Requisition.PENDING_APPROVAL, "approve");
        if (approver == null || approver.isBlank()) throw new IllegalArgumentException("approver required");
        r.setStatus(Requisition.OPEN);
        r.setApprovedBy(approver);
        r.setApprovedAt(LocalDateTime.now());
        return requisitionRepository.save(r);
    }

    @Transactional
    public Requisition sendBackToDraft(Long requisitionId, String reason) {
        Requisition r = mustGet(requisitionId);
        require(r.getStatus(), Requisition.PENDING_APPROVAL, "reject-to-draft");
        r.setStatus(Requisition.DRAFT);
        r.setClosedReason("Sent back on approval: " + (reason == null ? "" : reason));
        return requisitionRepository.save(r);
    }

    @Transactional
    public Requisition placeOnHold(Long requisitionId, String reason) {
        Requisition r = mustGet(requisitionId);
        require(r.getStatus(), Requisition.OPEN, "hold");
        r.setStatus(Requisition.ON_HOLD);
        r.setClosedReason(reason);
        return requisitionRepository.save(r);
    }

    @Transactional
    public Requisition resume(Long requisitionId) {
        Requisition r = mustGet(requisitionId);
        require(r.getStatus(), Requisition.ON_HOLD, "resume");
        r.setStatus(Requisition.OPEN);
        r.setClosedReason(null);
        return requisitionRepository.save(r);
    }

    @Transactional
    public Requisition close(Long requisitionId, String reason) {
        Requisition r = mustGet(requisitionId);
        if (Requisition.DRAFT.equals(r.getStatus()) || Requisition.PENDING_APPROVAL.equals(r.getStatus())) {
            throw new IllegalStateException("Only OPEN/ON_HOLD requisitions can be closed");
        }
        r.setStatus(Requisition.CLOSED);
        r.setClosedReason(reason);
        return requisitionRepository.save(r);
    }

    /** Link a Job to an OPEN requisition. Re-linking the same job replaces the old link. */
    @Transactional
    public JobRequisitionLink linkJob(Long requisitionId, Long jobId) {
        Requisition r = mustGet(requisitionId);
        if (!Requisition.OPEN.equals(r.getStatus())) {
            throw new IllegalStateException("Requisition " + r.getReqCode() + " is not OPEN (status=" + r.getStatus() + ")");
        }
        if (jobId == null) throw new IllegalArgumentException("jobId required");
        linkRepository.deleteById(jobId);
        JobRequisitionLink link = new JobRequisitionLink();
        link.setJobId(jobId);
        link.setRequisitionId(requisitionId);
        return linkRepository.save(link);
    }

    /** Publish gate: a Job may go live ONLY if linked to an OPEN requisition. Called by the Job publish path (see README). */
    public void assertCanPublishJob(Long jobId) {
        Requisition r = requisitionForJob(jobId)
                .orElseThrow(() -> new IllegalStateException(
                        "Job " + jobId + " cannot publish: no requisition linked (create + approve + link first)"));
        if (!Requisition.OPEN.equals(r.getStatus())) {
            throw new IllegalStateException(
                    "Job " + jobId + " cannot publish: requisition " + r.getReqCode() + " status=" + r.getStatus());
        }
    }

    public Optional<Requisition> requisitionForJob(Long jobId) {
        return linkRepository.findById(jobId)
                .flatMap(l -> requisitionRepository.findById(l.getRequisitionId()));
    }

    public List<Requisition> recent() {
        return requisitionRepository.findTop50ByOrderByCreatedAtDesc();
    }

    public List<Requisition> open() {
        return requisitionRepository.findByStatusOrderByCreatedAtDesc(Requisition.OPEN);
    }

    private Requisition mustGet(Long id) {
        return requisitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Requisition not found: " + id));
    }

    private static void require(String actual, String expected, String action) {
        if (!expected.equals(actual)) {
            throw new IllegalStateException("Cannot " + action + " requisition in status " + actual + " (expected " + expected + ")");
        }
    }
}
