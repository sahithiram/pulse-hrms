package com.mentis.hrms.service;

import com.mentis.hrms.model.Interview;
import com.mentis.hrms.model.InterviewFeedback;
import com.mentis.hrms.repository.InterviewFeedbackRepository;
import com.mentis.hrms.repository.InterviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * REQ-7-03 — interviews + feedback with visibility rule.
 * Feedback visibility: HR sees everything. An interviewer sees own feedback; theirs+everyone's ONLY after they submit.
 */
@Service
public class InterviewService {

    public static final Set<String> RECOMMENDATIONS = Set.of("STRONG_YES", "YES", "NO", "STRONG_NO");

    @Autowired private InterviewRepository interviewRepository;
    @Autowired private InterviewFeedbackRepository feedbackRepository;
    @Autowired private HrmsNotificationService notificationService;

    @Transactional
    public Interview schedule(Long applicationId, int round, List<String> interviewers, LocalDateTime scheduledAt,
                              String mode, String linkOrLocation) {
        if (applicationId == null || interviewers == null || interviewers.isEmpty() || scheduledAt == null) {
            throw new IllegalArgumentException("applicationId, interviewers and scheduledAt are required");
        }
        Interview i = new Interview();
        i.setApplicationId(applicationId);
        i.setRound(Math.max(round, 1));
        i.setInterviewers(String.join(",", interviewers.stream().map(String::trim).filter(s -> !s.isEmpty()).toList()));
        i.setScheduledAt(scheduledAt);
        i.setMode(mode == null ? "VIDEO" : mode);
        i.setLinkLocation(linkOrLocation);
        i.setStatus("SCHEDULED");
        Interview saved = interviewRepository.save(i);
        Map<String, String> ph = Map.of(
                "title", "Interview scheduled — application #" + applicationId,
                "body", "Round " + saved.getRound() + " on " + scheduledAt + " (" + saved.getMode() + ")");
        for (String interviewer : interviewers) {
            notificationService.notify(interviewer.trim(), "GENERIC", ph,
                    "/recruitment/interviews/" + saved.getId(), "INT-" + saved.getId() + "-" + interviewer.trim());
        }
        return saved;
    }

    @Transactional
    public Interview markStatus(Long interviewId, String status) {
        Interview i = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + interviewId));
        if (!List.of("SCHEDULED", "COMPLETED", "CANCELLED", "NO_SHOW").contains(status)) {
            throw new IllegalArgumentException("Bad interview status: " + status);
        }
        i.setStatus(status);
        return interviewRepository.save(i);
    }

    @Transactional
    public InterviewFeedback recordFeedback(Long interviewId, String interviewerId,
                                            String competencyRatingsJson, String recommendation, String notes) {
        if (interviewerId == null || interviewerId.isBlank()) throw new IllegalArgumentException("interviewerId required");
        String rec = recommendation == null ? "" : recommendation.trim().toUpperCase(Locale.ROOT);
        if (!RECOMMENDATIONS.contains(rec)) throw new IllegalArgumentException("recommendation must be one of " + RECOMMENDATIONS);
        Interview i = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + interviewId));
        List<String> listed = interviewerIds(i.getInterviewers());
        if (!listed.contains(interviewerId)) {
            throw new IllegalStateException("Interviewer " + interviewerId + " is not listed on interview " + interviewId);
        }
        InterviewFeedback fb = feedbackRepository.findByInterviewIdAndInterviewerId(interviewId, interviewerId)
                .orElseGet(InterviewFeedback::new);
        fb.setInterviewId(interviewId);
        fb.setInterviewerId(interviewerId);
        fb.setCompetencyRatings(competencyRatingsJson);
        fb.setRecommendation(rec);
        fb.setNotes(notes);
        fb.setSubmittedAt(LocalDateTime.now());
        return feedbackRepository.save(fb);
    }

    /**
     * Visibility rule: HR → all. Listed interviewer → own row + (only if they submitted) everyone else's.
     * Unlisted / other → nothing.
     */
    public List<Map<String, Object>> visibleFeedback(Long interviewId, String requesterId, boolean isHr) {
        Interview i = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new IllegalArgumentException("Interview not found: " + interviewId));
        List<String> listed = interviewerIds(i.getInterviewers());
        List<InterviewFeedback> all = feedbackRepository.findByInterviewId(interviewId);
        boolean requesterListed = listed.contains(requesterId);
        boolean requesterSubmitted = all.stream().anyMatch(f -> f.getInterviewerId().equals(requesterId));

        List<InterviewFeedback> visible;
        if (isHr) visible = all;
        else if (requesterListed && requesterSubmitted) visible = all;
        else if (requesterListed) visible = all.stream().filter(f -> f.getInterviewerId().equals(requesterId)).toList();
        else visible = List.of();

        return visible.stream().map(f -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("interviewId", f.getInterviewId());
            m.put("interviewerId", f.getInterviewerId());
            m.put("recommendation", f.getRecommendation());
            m.put("competencyRatings", f.getCompetencyRatings());
            m.put("notes", f.getNotes());
            m.put("submittedAt", f.getSubmittedAt().toString());
            return m;
        }).collect(Collectors.toList());
    }

    public static List<String> interviewerIds(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(",")).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toList());
    }

    public List<com.mentis.hrms.model.Interview> interviewsFor(Long applicationId) {
        return interviewRepository.findByApplicationIdOrderByScheduledAtAsc(applicationId);
    }

    public boolean interviewerHasSubmitted(Long interviewId, String interviewerId) {
        return feedbackRepository.findByInterviewIdAndInterviewerId(interviewId, interviewerId).isPresent();
    }
}
