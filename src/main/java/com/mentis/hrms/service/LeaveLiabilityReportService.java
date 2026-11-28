package com.mentis.hrms.service;

import com.mentis.hrms.repository.LeaveLedgerReadRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

/** REQ-6-10 reference report — leave liability from the LEDGER (authoritative), not cached balances. */
@Service
public class LeaveLiabilityReportService {

    @Autowired
    private LeaveLedgerReadRepository ledgerReadRepository;

    public Map<String, Object> liability() {
        List<Object[]> rows = ledgerReadRepository.liabilityByType();
        List<Map<String, Object>> perType = new ArrayList<>();
        BigDecimal grand = BigDecimal.ZERO;
        for (Object[] row : rows) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("code", row[0]);
            m.put("name", row[1]);
            m.put("balanceDays", row[2]);
            m.put("employees", row[3]);
            grand = grand.add((BigDecimal) row[2]);
            perType.add(m);
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("generatedAt", java.time.LocalDateTime.now().toString());
        report.put("source", "leave_ledger (SUM of append-only rows)");
        report.put("perType", perType);
        report.put("totalOutstandingDays", grand);
        return report;
    }
}
