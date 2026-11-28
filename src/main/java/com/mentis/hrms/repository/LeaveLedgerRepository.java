package com.mentis.hrms.repository;

import com.mentis.hrms.model.LeaveLedger;
import com.mentis.hrms.model.enums.LeaveEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface LeaveLedgerRepository extends JpaRepository<LeaveLedger, Long> {

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LeaveLedger l WHERE l.employeeId = :employeeId AND l.leaveTypeId = :leaveTypeId")
    BigDecimal sumAmount(@Param("employeeId") String employeeId, @Param("leaveTypeId") Long leaveTypeId);

    @Query("SELECT COALESCE(SUM(l.amount), 0) FROM LeaveLedger l WHERE l.employeeId = :employeeId")
    BigDecimal sumAmountForEmployee(@Param("employeeId") String employeeId);

    List<LeaveLedger> findByEmployeeIdAndLeaveTypeIdOrderByEntryDateAscIdAsc(String employeeId, Long leaveTypeId);

    boolean existsByReferenceTypeAndReferenceIdAndEntryTypeAndLeaveTypeIdAndEmployeeId(
            String referenceType, String referenceId, LeaveEntryType entryType, Long leaveTypeId, String employeeId);
}
