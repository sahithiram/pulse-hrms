package com.mentis.hrms.model.enums;

/** Lifecycle states. Allowed transitions are enforced by EmployeeOrgService, not just SQL. */
public enum EmploymentStatus {
    ONBOARDING, PROBATION, CONFIRMED, NOTICE, EXITED, INACTIVE
}
