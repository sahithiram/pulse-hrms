# Menti — Employee Assistant
Backend: `ChatController.java` (/api/chat/send) + `ChatService.java` + `OpenRouterService.java`
(Groq LLM call). No explicit role check in controller — scoped to the calling employee's own
`employeeId` only (personal data, not company-wide).

## Identity / System Prompt
"You are Menti, the HR Assistant for Menti's IT Solutions HRMS." — but functionally this is
the personal/employee-facing assistant, restricted to the logged-in employee's own records.

## Skills it HAS
1. **Profile** — name, department, designation, employment type, work location, join date,
   document deadline
2. **Today's attendance** — status, check-in/check-out time, working hours
3. **Monthly attendance summary** — working days, present/absent/leave days, % attendance,
   total hours, overtime, late arrivals, early departures (for any month/year the employee asks
   for — supports "this month", "last month", "next month", named months, "last N days")
4. **Recent attendance log** — last 7 days (date, day, check-in, check-out, hours, status)
5. **Leave balance** — sick / casual / earned: allocated, used, remaining (earned shown as
   "Unlimited" once over 900 in the raw balance)
6. **Pending leave requests** — own leave type, dates, day count
7. **Company holidays** — full list, sorted by date
8. **Document status** — own onboarding documents: total, pending/submitted/verified/rejected
   counts, and which MANDATORY documents still need attention
9. **Notifications** — recent notifications with read/unread state
10. **Company announcements** — READ-ONLY, can summarize what's currently posted
11. **Company policy answers** (hard-coded, not DB-driven): working hours 9AM–6PM (8h/day,
    Mon–Fri), leave needs min. 2 days' notice + manager approval, Sick Leave = 12 days/year,
    Casual Leave = 7 days/year, Earned Leave = no fixed cap, check-in after 9:15 AM = late.

## Deterministic (non-LLM) fast-path answers
Before the LLM is even called, these patterns are matched directly against the database for
speed/accuracy:
- "Am I checked in?" / "check in status"
- "What time did I check in?"
- "What is my status today?" / "am I working?" / "am I active?"
- "Attendance percentage" (optionally for a specific month/year)
- "Last N days" / "last N weeks" / "this month" / named month attendance requests

## Actions it CAN PERFORM (not just read)
- **Apply for leave** — "apply for sick leave from 2026-08-01 to 2026-08-03, reason: fever"
  (leave type must be sick/casual/earned; dates must be YYYY-MM-DD; goes to PENDING, awaiting
  HR/Admin approval)
- **Withdraw leave** — "withdraw leave LV-2026-1234" or "withdraw my leave" (falls back to most
  recent PENDING leave if no ID given)
- **Cancel leave** — "cancel leave LV-2026-1234" (works on PENDING or APPROVED; restores leave
  balance if it was approved)

## What it does NOT do / limitations
- **Cannot see or act on any other employee's data** — every context build is scoped strictly
  to the requesting `employeeId`.
- **Cannot approve or reject its own or anyone's leave** — only apply/withdraw/cancel.
- **Cannot create, edit, or post announcements.**
- **Cannot generate/download reports** (no report/export endpoint exists on this controller,
  unlike the Admin and HR assistants).
- **Cannot add/edit/delete departments, locations, jobs, or employees.**
- Does not handle payroll/salary (module doesn't exist in this HRMS).
- If a data section fails to load it says "(... temporarily unavailable)" for that section only
  — it doesn't fail the whole response.
