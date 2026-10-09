# Menti Admin — Super Admin AI Assistant
Backend: `AdminAIController.java` (/api/chat/admin/send) + `AdminAIService.java` (Groq LLM call)
Access check: session `userRole` must be `SUPER_ADMIN`, else 403.

## Identity / System Prompt
"You are Menti Admin, the Super Admin AI Assistant for Menti's IT Solutions HRMS."
The Super Admin is told they have FULL, UNRESTRICTED access to every module and must
never say "not mentioned" or redirect to a tab/page if the data exists in context.

## Skills it HAS
1. **Employees** — full directory, profile, department, designation, employment type, status
2. **Attendance** — today's present/absent/on-leave/checked-out counts, per-employee
   check-in/check-out, working hours, late arrivals, early departures
3. **Monthly attendance summaries** — per employee, per month (e.g. "EMP001 June attendance"),
   including daily breakdown and attendance %
4. **Leave management** — all leave requests (pending/approved/rejected) org-wide, leave
   balances (sick/casual/earned) for every employee
5. **Recruitment** — all job postings, active jobs, all applications, applications-by-stage,
   offer letters issued/sent counts
6. **Onboarding documents** — status/verification per employee (context-dependent)
7. **Departments & designations** — full list with headcount
8. **Roles & permissions** — all role/permission rows (HR, HR_MANAGER, EMPLOYEE, etc.)
9. **Announcements** — can VIEW all announcements
10. **Holidays** — full holiday calendar + upcoming holidays
11. **Locations/offices** — full list, phone/email/address
12. Explicitly told: "This HRMS does NOT have a payroll/salary module" — must say so, not invent numbers.

## Actions it CAN PERFORM (not just read) —
These are detected by keyword-matching the message BEFORE it ever reaches the LLM:
- **Approve leave** — "approve leave LV-2026-1234" (optional `remarks: ...`)
- **Reject leave** — "reject leave LV-2026-1234, reason: ..."
- **Create/post announcement** — "create announcement: Title - Description"
  (only Super Admin can do this; HR/Employee cannot)
- **Add a location** — "add location: name=..., type=..., country=..., state=..., city=...,
  address=..., phone=..., email=..."
- **Update a location** — "update location: id=3, city=Bengaluru"
- **Delete a location** — "delete location: id=3"
- **Generate & download reports** — "download employee list as pdf", "export locations report
  as excel", "print attendance report as csv" — supports CSV / Excel (.xlsx) / PDF / Word (.docx)
  for: Employee Directory, Locations, Departments (partial). Returns a `downloadUrl:` the
  frontend turns into a download link (`GET /api/chat/admin/download/{token}`).

## What it does NOT do / limitations
- Cannot generate reports for every module yet — only employees, locations, departments are
  wired into the report generator (leave/attendance/recruitment reports are not yet routed
  through this action handler, only through raw chat answers).
- Cannot process payroll/salary (module doesn't exist in this HRMS).
- Cannot bulk-edit employee records via chat (only leave approve/reject and location CRUD are
  wired as actions).
- Cannot create employees, jobs, or offer letters via chat (read-only for those).
- If a context section fails to load (e.g. DB error), that section is marked
  "(unavailable: <error>)" rather than silently omitted — the AI is told never to blame this
  on "not mentioned in context."
- Onboarding-document **org-wide** listing is explicitly noted as NOT wired into the global
  context yet (only per-employee lookups exist) — flagged as a known gap in the code comments.
