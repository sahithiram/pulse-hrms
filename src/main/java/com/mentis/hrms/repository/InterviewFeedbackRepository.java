package com.mentis.hrms.repository;

import com.mentis.hrms.model.InterviewFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface InterviewFeedbackRepository extends JpaRepository<InterviewFeedback, Long> {
    List<InterviewFeedback> findByInterviewId(Long interviewId);
    Optional<InterviewFeedback> findByInterviewIdAndInterviewerId(Long interviewId, String interviewerId);
}
