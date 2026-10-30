package com.mentis.hrms.pm.realtime;

import com.mentis.hrms.pm.entity.Task;
import com.mentis.hrms.pm.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class PmEventPublisher {

    private static final Pattern TASK = Pattern.compile("^/api/pm/tasks/(\\d+)");
    private static final Pattern TEAM = Pattern.compile("^/api/pm/teams/(\\d+)");

    private final SimpMessagingTemplate messaging;
    private final TaskRepository taskRepository;

    /** Legacy 3-arg form — used by PmCurrentUserInterceptor. No team topic; the global topic only. */
    public void publish(String method, String path, String actor) {
        publish(method, path, actor, null);
    }

    /**
     * Publish to the global topic (Admin/PM/HR) and to every team topic that the path resolves to.
     * teamIds lets callers pass the team explicitly when the path alone can't tell us
     * (new task without id, delete after the row is gone, bulk calls with ids in the body).
     */
    public void publish(String method, String path, String actor, Collection<Long> teamIds) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("method", method);
        payload.put("path", path);
        payload.put("actor", actor);
        payload.put("at", System.currentTimeMillis());
        messaging.convertAndSend("/topic/pm/events", payload);

        Set<Long> teams = new LinkedHashSet<>();
        if (teamIds != null) {
            for (Long id : teamIds) if (id != null) teams.add(id);
        }
        String p = path == null ? "" : path;
        Matcher t = TASK.matcher(p);
        if (t.find()) {
            try {
                Long taskId = Long.valueOf(t.group(1));
                taskRepository.findById(taskId)
                        .map(Task::getTeamId)
                        .ifPresent(teams::add);
            } catch (NumberFormatException ignored) { }
        }
        Matcher m = TEAM.matcher(p);
        if (m.find()) {
            try { teams.add(Long.valueOf(m.group(1))); }
            catch (NumberFormatException ignored) { }
        }
        teams.forEach(id -> messaging.convertAndSend("/topic/pm/team/" + id, payload));
    }
}