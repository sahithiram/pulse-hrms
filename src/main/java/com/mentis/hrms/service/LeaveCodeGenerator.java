package com.mentis.hrms.service;

import com.mentis.hrms.repository.LeaveRequestSeqRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/** Generates LV-YYYY-NNNNNN human codes backed by leave_request_seq (atomic, collision-free). */
@Service
public class LeaveCodeGenerator {

    @Autowired
    private LeaveRequestSeqRepository seqRepository;

    public String nextCode() {
        int year = LocalDate.now().getYear();
        long n = seqRepository.next(year);
        return String.format("LV-%d-%06d", year, n);
    }
}
