package com.mentis.hrms.service;

import com.mentis.hrms.model.EmployeeShift;
import com.mentis.hrms.model.RosterEntry;
import com.mentis.hrms.model.Shift;
import com.mentis.hrms.repository.EmployeeShiftRepository;
import com.mentis.hrms.repository.RosterEntryRepository;
import com.mentis.hrms.repository.ShiftRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Effective shift for (employee, date): roster entry wins, then employee_shift (most recent effective_from ≤ date,
 * honouring effective_to), else empty. Sprint-3 WorkingDayService consults this to populate shiftRef.
 */
@Service
@Transactional(readOnly = true)
public class ShiftResolver {

    @Autowired private ShiftRepository shiftRepository;
    @Autowired private EmployeeShiftRepository employeeShiftRepository;
    @Autowired private RosterEntryRepository rosterEntryRepository;

    public Optional<Shift> resolve(String employeeId, LocalDate date) {
        Optional<RosterEntry> roster = rosterEntryRepository.findByEmployeeIdAndRosterDate(employeeId, date);
        if (roster.isPresent()) {
            return shiftRepository.findById(roster.get().getShiftId());
        }
        return employeeShiftRepository
                .findByEmployeeIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(employeeId, date)
                .stream()
                .filter(es -> es.isEffectiveOn(date))
                .findFirst()
                .flatMap(es -> shiftRepository.findById(es.getShiftId()));
    }
}
