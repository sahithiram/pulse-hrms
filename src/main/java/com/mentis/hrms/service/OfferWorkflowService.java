package com.mentis.hrms.service;

import com.mentis.hrms.model.OfferWorkflow;
import com.mentis.hrms.model.OfferRevision;
import com.mentis.hrms.model.OfferLetter;
import com.mentis.hrms.repository.OfferRevisionRepository;
import com.mentis.hrms.repository.OfferWorkflowRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mentis.hrms.repository.OfferLetterRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * REQ-7-04 — offer approval + validity + version history. Augments legacy offer_letters (never alters it).
 * Status machine: DRAFT → PENDING_APPROVAL → APPROVED → SENT → ACCEPTED|DECLINED|EXPIRED|WITHDRAWN;
 * REVISE from DRAFT/PENDING_APPROVAL/APPROVED/SENT → snapshot to offer_revision, version+1, status=REVISED → then DRAFT again.
 */
@Service
public class OfferWorkflowService {

    @Autowired private OfferWorkflowRepository workflowRepository;
    @Autowired private OfferRevisionRepository revisionRepository;
    @Autowired private OfferLetterRepository offerLetterRepository;
    @Autowired private OfferHandoffService handoffService;

    @Transactional
    public OfferWorkflow createForOfferLetter(Long offerLetterId, LocalDate validUntil) {
        if (workflowRepository.findByOfferLetterId(offerLetterId).isPresent()) {
            throw new IllegalStateException("Workflow already exists for offer " + offerLetterId);
        }
        OfferWorkflow w = new OfferWorkflow();
        w.setOfferLetterId(offerLetterId);
        w.setStatus(OfferWorkflow.DRAFT);
        w.setVersion(1);
        w.setValidUntil(validUntil);
        return workflowRepository.save(w);
    }

    @Transactional
    public OfferWorkflow submitForApproval(Long offerLetterId) {
        OfferWorkflow w = mustGet(offerLetterId);
        require(w, OfferWorkflow.DRAFT, "submit");
        w.setStatus(OfferWorkflow.PENDING_APPROVAL);
        return workflowRepository.save(w);
    }

    @Transactional
    public OfferWorkflow approve(Long offerLetterId, String hrApprover, String budgetApprover) {
        OfferWorkflow w = mustGet(offerLetterId);
        require(w, OfferWorkflow.PENDING_APPROVAL, "approve");
        if (hrApprover == null || hrApprover.isBlank()) throw new IllegalArgumentException("HR approver required");
        w.setApproverHr(hrApprover);
        w.setApproverBudget(budgetApprover);
        w.setApprovedAt(LocalDateTime.now());
        w.setStatus(OfferWorkflow.APPROVED);
        return workflowRepository.save(w);
    }

    /** Called by the existing send path once the mail is out (see README — one line to wire). */
    @Transactional
    public OfferWorkflow markSent(Long offerLetterId) {
        OfferWorkflow w = mustGet(offerLetterId);
        require(w, OfferWorkflow.APPROVED, "send");
        w.setStatus(OfferWorkflow.SENT);
        return workflowRepository.save(w);
    }

    /** Accept → hand-off: creates ONBOARDING employee + HIRED stage in the SAME transaction. */
    @Transactional
    public OfferWorkflow accept(Long offerLetterId, String acceptedBy) {
        OfferWorkflow w = mustGet(offerLetterId);
        require(w, OfferWorkflow.SENT, "accept");
        if (w.getValidUntil() != null && w.getValidUntil().isBefore(LocalDate.now())) {
            throw new IllegalStateException("Offer " + offerLetterId + " expired on " + w.getValidUntil());
        }
        handoffService.handoffOnAcceptance(offerLetterId, acceptedBy);
        w.setStatus(OfferWorkflow.ACCEPTED);
        return workflowRepository.save(w);
    }

    @Transactional
    public OfferWorkflow decline(Long offerLetterId) {
        OfferWorkflow w = mustGet(offerLetterId);
        require(w, OfferWorkflow.SENT, "decline");
        w.setStatus(OfferWorkflow.DECLINED);
        return workflowRepository.save(w);
    }

    @Transactional
    public OfferWorkflow withdraw(Long offerLetterId, String reason) {
        OfferWorkflow w = mustGet(offerLetterId);
        if (List.of(OfferWorkflow.ACCEPTED, OfferWorkflow.DECLINED, OfferWorkflow.EXPIRED, OfferWorkflow.WITHDRAWN)
                .contains(w.getStatus())) {
            throw new IllegalStateException("Cannot withdraw offer in terminal status " + w.getStatus());
        }
        w.setStatus(OfferWorkflow.WITHDRAWN);
        return workflowRepository.save(w);
    }

    /** Snapshot CURRENT offer content into offer_revision (version N), bump version, back to DRAFT. */
    @Transactional
    public OfferWorkflow revise(Long offerLetterId, String changeReason, String revisedBy) {
        OfferWorkflow w = mustGet(offerLetterId);
        if (List.of(OfferWorkflow.ACCEPTED, OfferWorkflow.WITHDRAWN, OfferWorkflow.EXPIRED).contains(w.getStatus())) {
            throw new IllegalStateException("Cannot revise offer in terminal status " + w.getStatus());
        }
        if (changeReason == null || changeReason.isBlank()) {
            throw new IllegalArgumentException("changeReason is required for a revision");
        }
        OfferLetter offer = offerLetterRepository.findById(offerLetterId)
                .orElseThrow(() -> new IllegalArgumentException("OfferLetter not found: " + offerLetterId));

        OfferRevision rev = new OfferRevision();
        rev.setOfferLetterId(offerLetterId);
        rev.setVersion(w.getVersion());
        rev.setDesignation(offer.getDesignation());
        rev.setDepartment(offer.getDepartment());
        rev.setAnnualSalary(offer.getAnnualSalary());
        rev.setCurrency(offer.getCurrency());
        rev.setJoiningDate(offer.getJoiningDate());
        rev.setEmploymentType(offer.getEmploymentType());
        rev.setChangeReason(changeReason);
        rev.setRevisedBy(revisedBy);
        revisionRepository.save(rev);

        w.setVersion(w.getVersion() + 1);
        w.setStatus(OfferWorkflow.DRAFT);
        w.setApprovedAt(null);
        w.setApproverHr(null);
        w.setApproverBudget(null);
        return workflowRepository.save(w);
    }

    public Optional<OfferWorkflow> workflow(Long offerLetterId) {
        return workflowRepository.findByOfferLetterId(offerLetterId);
    }

    public List<OfferRevision> revisions(Long offerLetterId) {
        return revisionRepository.findByOfferLetterIdOrderByVersionDesc(offerLetterId);
    }

    private OfferWorkflow mustGet(Long offerLetterId) {
        return workflowRepository.findByOfferLetterId(offerLetterId).orElseGet(() -> {
            // Late-binding: workflow may not have been created at offer-draft time; create lazily as DRAFT.
            OfferWorkflow w = new OfferWorkflow();
            w.setOfferLetterId(offerLetterId);
            w.setStatus(OfferWorkflow.DRAFT);
            return workflowRepository.save(w);
        });
    }

    private static void require(OfferWorkflow w, String expected, String action) {
        if (!expected.equals(w.getStatus())) {
            throw new IllegalStateException("Cannot " + action + " offer in status " + w.getStatus() + " (expected " + expected + ")");
        }
    }
}
