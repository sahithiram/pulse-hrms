package com.mentis.hrms.pm.scheduler;

import com.mentis.hrms.model.Employee;
import com.mentis.hrms.pm.entity.Milestone;
import com.mentis.hrms.pm.entity.Project;
import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.enums.MilestoneStatus;
import com.mentis.hrms.pm.enums.ProjectRole;
import com.mentis.hrms.pm.enums.ProjectStatus;
import com.mentis.hrms.pm.enums.TaskStatus;
import com.mentis.hrms.pm.repository.MilestoneRepository;
import com.mentis.hrms.pm.repository.ProjectMemberRepository;
import com.mentis.hrms.pm.repository.ProjectRepository;
import com.mentis.hrms.pm.repository.TaskRepository;
import com.mentis.hrms.pm.security.PmAccessService;
import com.mentis.hrms.repository.EmployeeRepository;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/**
 * Monday 08:00 email to each project owner / MANAGER / LEAD: overdue tasks, blocked tasks and
 * milestones due in the next 14 days (or already late), for every open project they run.
 * One email per person, even if they run several projects. Projects with nothing to report are skipped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ProjectWeeklyDigestScheduler {

    private static final int MAX_ROWS = 15;
    private static final int MILESTONE_WINDOW_DAYS = 14;
    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final ProjectRepository projectRepository;
    private final ProjectMemberRepository projectMemberRepository;
    private final TaskRepository taskRepository;
    private final MilestoneRepository milestoneRepository;
    private final EmployeeRepository employeeRepository;
    private final PmAccessService access;
    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}") private String fromEmail;
    @Value("${pm.digest.enabled:true}") private boolean enabled;
    @Value("${app.base-url:http://localhost:8080}") private String baseUrl;

    private record Block(Project project, List<Task> overdue, List<Task> blocked, List<Milestone> milestones) {}

    /** Everything one run needs, loaded once. */
    private record Data(LocalDate today, List<Project> openProjects, Map<Long, List<Task>> tasksByProject,
                        Map<Long, List<Milestone>> milestonesByProject, Map<Long, Set<Long>> leadersByProject) {}

    @Scheduled(cron = "${pm.digest.cron:0 0 8 * * MON}", zone = "${pm.digest.zone:Asia/Kolkata}")
    public void weekly() {
        if (!enabled) return;
        DigestResult r = runNow();
        log.info("Weekly PM digest: sent={}, skipped={}, failed={}", r.sent(), r.skipped(), r.failed());
    }

    /** Also called by the manual trigger endpoint. */
    public DigestResult runNow() {
        int sent = 0, skipped = 0, failed = 0;
        Data data;
        List<Employee> recipients;
        try {
            data = load();
            recipients = digestRecipients(data);
        } catch (Exception ex) {
            log.warn("Digest could not load its data: {}", ex.getMessage(), ex);
            return new DigestResult(0, 0, 1);
        }
        for (Employee e : recipients) {
            String to = e.getEmail();
            try {
                if (to == null || to.isBlank()) {
                    skipped++;
                    log.info("Digest skipped (no email address): employee pk={}", e.getId());
                    continue;
                }
                if (buildAndSend(e, data)) sent++;
                else { skipped++; log.info("Digest skipped (nothing to report): {}", to); }
            } catch (Exception ex) {
                failed++;
                log.warn("Digest FAILED for {}: {}", to, ex.getMessage());
            }
        }
        log.info("Weekly digest finished: sent={}, skipped={}, failed={}", sent, skipped, failed);
        return new DigestResult(sent, skipped, failed);
    }

    // ───────── data ─────────

    private Data load() {
        LocalDate today = LocalDate.now(ZONE);
        List<Project> open = new ArrayList<>();
        for (Project p : projectRepository.findAll()) {
            if (p.getStatus() != ProjectStatus.COMPLETED && p.getStatus() != ProjectStatus.ARCHIVED) open.add(p);
        }
        Set<Long> openIds = new HashSet<>();
        open.forEach(p -> openIds.add(p.getId()));

        Map<Long, List<Task>> tasks = new HashMap<>();
        for (Task t : taskRepository.findAll()) {
            if (t.getProjectId() != null && openIds.contains(t.getProjectId()))
                tasks.computeIfAbsent(t.getProjectId(), k -> new ArrayList<>()).add(t);
        }
        Map<Long, List<Milestone>> miles = new HashMap<>();
        for (Milestone m : milestoneRepository.findAll()) {
            if (openIds.contains(m.getProjectId()))
                miles.computeIfAbsent(m.getProjectId(), k -> new ArrayList<>()).add(m);
        }
        Map<Long, Set<Long>> leaders = new HashMap<>();
        for (Project p : open) {
            Set<Long> set = leaders.computeIfAbsent(p.getId(), k -> new LinkedHashSet<>());
            if (p.getOwnerId() != null) set.add(p.getOwnerId());
        }
        for (var m : projectMemberRepository.findAll()) {
            if (m.getRole() == ProjectRole.MANAGER || m.getRole() == ProjectRole.LEAD) {
                Set<Long> set = leaders.get(m.getProjectId());
                if (set != null) set.add(m.getEmployeeId());
            }
        }
        return new Data(today, open, tasks, miles, leaders);
    }

    private List<Employee> digestRecipients(Data d) {
        Set<Long> ids = new LinkedHashSet<>();
        d.leadersByProject().values().forEach(ids::addAll);
        return employeeRepository.findAllById(ids);
    }

    /** @return false when nothing to report (nothing is sent). */
    private boolean buildAndSend(Employee emp, Data d) throws Exception {
        List<Block> blocks = new ArrayList<>();
        LocalDate windowEnd = d.today().plusDays(MILESTONE_WINDOW_DAYS);

        for (Project p : d.openProjects()) {
            if (!d.leadersByProject().getOrDefault(p.getId(), Set.of()).contains(emp.getId())) continue;

            List<Task> overdue = new ArrayList<>(), blocked = new ArrayList<>();
            for (Task t : d.tasksByProject().getOrDefault(p.getId(), List.of())) {
                if (t.getStatus() == TaskStatus.DONE) continue;
                if (t.getStatus() == TaskStatus.BLOCKED) blocked.add(t);
                if (t.getDueDate() != null && t.getDueDate().isBefore(d.today())) overdue.add(t);
            }
            overdue.sort(Comparator.comparing(Task::getDueDate));

            List<Milestone> ms = new ArrayList<>();
            for (Milestone m : d.milestonesByProject().getOrDefault(p.getId(), List.of())) {
                boolean openMs = m.getStatus() == MilestoneStatus.OPEN || m.getStatus() == MilestoneStatus.AT_RISK;
                if (openMs && m.getDueDate() != null && !m.getDueDate().isAfter(windowEnd)) ms.add(m);
            }
            ms.sort(Comparator.comparing(Milestone::getDueDate));

            if (overdue.isEmpty() && blocked.isEmpty() && ms.isEmpty()) continue;
            blocks.add(new Block(p, overdue, blocked, ms));
        }
        if (blocks.isEmpty()) return false;

        MimeMessage msg = mailSender.createMimeMessage();
        MimeMessageHelper h = new MimeMessageHelper(msg, false, "UTF-8");
        if (fromEmail != null && !fromEmail.isBlank()) h.setFrom(fromEmail);
        h.setTo(emp.getEmail());
        h.setSubject("Weekly project digest – " + d.today());
        h.setText(html(emp, blocks, d.today()), true);
        mailSender.send(msg);
        return true;
    }

    // ───────── email body ─────────

    private String html(Employee emp, List<Block> blocks, LocalDate today) {
        StringBuilder sb = new StringBuilder();
        sb.append("<div style=\"font-family:Arial,sans-serif;max-width:640px;line-height:1.5\">")
                .append("<h2>Weekly project digest</h2><p>Hi ").append(esc(first(emp)))
                .append(", here is where your projects stand on ").append(today).append(".</p>");

        for (Block b : blocks) {
            sb.append("<h3 style=\"margin-bottom:4px\">").append(esc(b.project().getName())).append("</h3>");

            if (!b.milestones().isEmpty()) {
                sb.append("<p style=\"margin:8px 0 2px\"><b>Milestones</b></p><ul style=\"margin-top:0\">");
                for (Milestone m : b.milestones()) {
                    boolean late = m.getDueDate().isBefore(today);
                    sb.append("<li>").append(esc(m.getName())).append(" – ")
                            .append(late ? "<span style=\"color:#c62828\">LATE, was due " : "due ")
                            .append(m.getDueDate()).append(late ? "</span>" : "").append("</li>");
                }
                sb.append("</ul>");
            }
            taskList(sb, "Overdue tasks", b.overdue(), today);
            taskList(sb, "Blocked tasks", b.blocked(), today);
        }
        sb.append("<p><a href=\"").append(esc(baseUrl)).append("/pm/06-tasks\">Open the task board</a></p>")
                .append("<p style=\"color:#777;font-size:12px\">Automated message. Sent every Monday to project owners, managers and leads.</p></div>");
        return sb.toString();
    }

    private void taskList(StringBuilder sb, String heading, List<Task> tasks, LocalDate today) {
        if (tasks.isEmpty()) return;
        sb.append("<p style=\"margin:8px 0 2px\"><b>").append(heading).append(" (").append(tasks.size()).append(")</b></p>")
                .append("<ul style=\"margin-top:0\">");
        for (int i = 0; i < Math.min(tasks.size(), MAX_ROWS); i++) {
            Task t = tasks.get(i);
            sb.append("<li>").append(esc(t.getTitle()));
            if (t.getDueDate() != null) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(t.getDueDate(), today);
                if (days > 0) sb.append(" – ").append(days).append(" day(s) late");
            }
            sb.append("</li>");
        }
        if (tasks.size() > MAX_ROWS) sb.append("<li>… and ").append(tasks.size() - MAX_ROWS).append(" more</li>");
        sb.append("</ul>");
    }

    private String first(Employee e) {
        return e.getFirstName() == null || e.getFirstName().isBlank() ? "there" : e.getFirstName();
    }

    private String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}