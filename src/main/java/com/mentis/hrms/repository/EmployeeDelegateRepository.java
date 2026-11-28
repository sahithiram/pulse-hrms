package com.mentis.hrms.repository;

import com.mentis.hrms.model.EmployeeDelegate;
import com.mentis.hrms.model.enums.DelegateScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.time.LocalDate;
import java.util.List;

@Repository
public interface EmployeeDelegateRepository extends JpaRepository<EmployeeDelegate, Long> {
    List<EmployeeDelegate> findByDelegatorId(Long delegatorId);
    List<EmployeeDelegate> findByDelegateIdAndFromDateLessThanEqualAndToDateGreaterThanEqualAndScopeIn(
            Long delegateId, LocalDate from, LocalDate to, List<DelegateScope> scopes);
}
