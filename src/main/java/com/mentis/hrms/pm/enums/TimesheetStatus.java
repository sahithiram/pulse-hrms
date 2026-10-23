package com.mentis.hrms.pm.enums;

public enum TimesheetStatus {
    DRAFT,       // being filled in, editable
    SUBMITTED,   // waiting for the approver, locked
    APPROVED,    // final, locked
    REJECTED     // sent back with a note, editable again
}