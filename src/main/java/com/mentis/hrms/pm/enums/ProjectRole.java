package com.mentis.hrms.pm.enums;

public enum ProjectRole {
    MANAGER, // administer the project: dates, baseline, closure, members, finance
    LEAD,    // STEP 1: tasks, sprints, review, team timesheets. No finance, baseline or closure.
    MEMBER,  // view + work on tasks assigned to them
    VIEWER   // read-only
}