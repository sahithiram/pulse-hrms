package com.mentis.hrms.pm.dto.response;

import lombok.*;

import java.util.List;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class PmMeResponse {
    private Long id;
    private String employeeId;
    private String name;
    private String email;
    private String avatarInitials;
    private String hrmsRole;
    private List<Long> ownerOfProjectIds;
    private List<Long> leadOfTeamIds;
    private List<Long> memberOfTeamIds;
    /** Read-level company admin: Super Admin, HR, HR_MANAGER (sees all projects). */
    private boolean admin;
    /** Super Admin or PM only. HR no longer creates projects directly. */
    private boolean canCreateProject;

    // ── STEP 1 additions ──
    private boolean superAdmin;      // full control
    private boolean peopleAdmin;     // HR / HR_MANAGER: read-only on projects
    private boolean intakeReviewer;  // may approve / reject project requests and define templates
    private boolean pm;              // global PM role
    private boolean lead;            // team lead OR explicit LEAD role on any project (7.0)
    private boolean financeAccess;   // Super Admin, or owns / manages at least one project

    // ── STEP 1b additions ──
    private boolean canCreateTeam;     // admins (incl. HR) and PMs
    private boolean canManageClients;  // admins (incl. HR) and PMs

    // ── STEP 7.0 addition ──
    /**
     * Anyone who runs work day to day: Super Admin, PM, team lead, explicit project LEAD,
     * or owner / MANAGER of any project. HR is NOT in this tier (HR uses `admin`).
     * The UI uses it for Backlog, Sprints, Reports, review queue and similar.
     */
    private boolean leadTier;
}