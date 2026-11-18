package com.mentis.hrms.pm.dto.response;

import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.DuplicateResourceException;
import com.mentis.hrms.pm.exception.InvalidOperationException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import lombok.Getter;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;

/** Outcome of adding many people: who went in, and who was skipped and why. */
@Getter
public class BulkAddResult {

    public record Skipped(Long employeeId, String reason) {}

    private final List<Long> addedIds = new ArrayList<>();
    private final List<Skipped> skipped = new ArrayList<>();

    /**
     * Runs one person's add. Any rule failure (duplicate, over-allocated, not found,
     * or a DB-level unique violation) skips that person only.
     */
    public void attempt(Long employeeId, Runnable add) {
        try {
            add.run();
            addedIds.add(employeeId);
        } catch (ResourceNotFoundException | DuplicateResourceException
                 | BusinessRuleException | InvalidOperationException
                 | DataIntegrityViolationException e) {
            skipped.add(new Skipped(employeeId, e.getMessage()));
        }
    }

    public String summary() {
        return addedIds.size() + " added" + (skipped.isEmpty() ? "" : ", " + skipped.size() + " skipped");
    }
}