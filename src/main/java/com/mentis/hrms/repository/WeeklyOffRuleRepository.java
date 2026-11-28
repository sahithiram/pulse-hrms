package com.mentis.hrms.repository;

import com.mentis.hrms.model.WeeklyOffRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WeeklyOffRuleRepository extends JpaRepository<WeeklyOffRule, Long> {
    List<WeeklyOffRule> findByCalendarId(Long calendarId);
    void deleteByCalendarId(Long calendarId);
}
