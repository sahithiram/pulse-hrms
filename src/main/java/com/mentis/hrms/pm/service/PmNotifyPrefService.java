package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.entity.PmNotifyPref;
import com.mentis.hrms.pm.repository.PmNotifyPrefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PmNotifyPrefService {

    private final PmNotifyPrefRepository repo;

    @Transactional(readOnly = true)
    public PmNotifyPref get(Long employeeId) {
        return repo.findById(employeeId).orElseGet(() -> {          // defaults, not saved
            PmNotifyPref p = new PmNotifyPref();
            p.setEmployeeId(employeeId);
            return p;
        });
    }

    @Transactional
    public PmNotifyPref save(Long employeeId, boolean emailHandover, boolean emailHighOnly) {
        PmNotifyPref p = get(employeeId);
        p.setEmailHandover(emailHandover);
        p.setEmailHighOnly(emailHighOnly);
        return repo.save(p);
    }
}