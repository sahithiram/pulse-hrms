# HR AI Assistant — Setup Instructions

All new files are already uploaded into your Drive project in the correct folders:

| File | Uploaded to |
|---|---|
| `HrAIChatSession.java` | `hrms/src/main/java/com/mentis/hrms/model/` |
| `HrAIChatMessage.java` | `hrms/src/main/java/com/mentis/hrms/model/` |
| `HrAIChatSessionRepository.java` | `hrms/src/main/java/com/mentis/hrms/repository/` |
| `HrAIChatMessageRepository.java` | `hrms/src/main/java/com/mentis/hrms/repository/` |
| `HrAIService.java` | `hrms/src/main/java/com/mentis/hrms/service/` |
| `HrAISessionService.java` | `hrms/src/main/java/com/mentis/hrms/service/` |
| `HrAIController.java` | `hrms/src/main/java/com/mentis/hrms/controller/` |
| `create_hr_ai_chat.sql` | `hrms/` (project root, next to `create_announcements.sql`) |
| `HrAIPanel.html` | `hrms/src/main/resources/templates/fragments/` |

I could not edit your two existing template files directly (Drive access here only lets me add
files, not modify existing ones), so do these two by hand — each is a single line.

---

## 1. Download the 9 new files above into your real local project

Since your Drive folder is an upload/backup rather than a live-synced copy, copy each of the 9
files above into the matching folder in your actual local `hrms` repo (same relative paths), then
also run the two edits below.

---

## 2. Edit `src/main/resources/templates/fragments/layout.html`

Inside the `sidebar(activeTab)` fragment, you already have a block like this (Organization
Settings, Super-Admin only):

```html
<!-- Organization Structure (SUPER ADMIN) -->
<div class="nav-section" th:if="${isSuperAdmin}">
    <i class="fas fa-building"></i> ORGANIZATION SETTINGS
</div>
<ul class="nav flex-column" th:if="${isSuperAdmin}">
    ...
</ul>
```

Directly **after** that block (or anywhere else inside the same `sidebar(activeTab)` `<div>`), add:

```html
<!-- HR AI Assistant panel — HR / HR_MANAGER only -->
<div th:if="${isHR and !isSuperAdmin}" th:replace="~{fragments/HrAIPanel :: hrAIPanel}"></div>
```

> Want Super Admin to see it too on `/dashboard/hr`? Change the condition to
> `th:if="${isHR}"` and add `"SUPER_ADMIN"` to `ALLOWED_ROLES` in `HrAIController.java`.

---

## 3. Edit `src/main/resources/templates/dashboard.html` — nothing needed

Since the JS for the HR panel is now inlined inside `HrAIPanel.html` itself (matching how your
other panels like `ai-popup.html` work), there is **no separate script include to add**. As soon as
`layout.html` renders the fragment for an HR/HR_MANAGER session, the panel and its behaviour load
automatically.

---

## 4. Run the SQL migration

Run `create_hr_ai_chat.sql` against your MySQL DB the same way you ran `create_announcements.sql`
(two new tables: `hr_ai_chat_sessions`, `hr_ai_chat_messages`).

---

## 5. Rebuild and test

1. `mvn clean install`
2. Log in as an `HR` (or `HR_MANAGER`) user → open `/dashboard/hr` → confirm the floating robot
   button appears bottom-right.
3. Ask: "What's today's attendance summary?" → real present/absent/checked-out counts.
4. Ask: "How many pending leave requests are there?" → lists them with employee IDs and dates.
5. Ask: "Write an announcement about the new holiday policy" → should politely refuse and point to
   Super Admin / the Announcements module.
6. Log in as `SUPER_ADMIN` → confirm the HR panel does **not** appear (unless you opted into
   showing it there in step 2) and the existing Admin AI panel still works unchanged.
7. Log in as `EMPLOYEE` → confirm nothing changed on their dashboard.

---

## How the three assistants now coexist

| Assistant | Endpoint | Who can call it | Can't do |
|---|---|---|---|
| Employee | `POST /api/chat/send` | Logged-in employee | See other employees' data |
| **HR (new)** | `POST /api/chat/hr/send` | `HR`, `HR_MANAGER` | Draft/post announcements |
| Super Admin | `POST /api/chat/admin/send` | `SUPER_ADMIN` | *(already unrestricted)* |

None of the three share tables, controllers, or services — fully independent, so your existing
Employee/Super Admin assistants are untouched.
