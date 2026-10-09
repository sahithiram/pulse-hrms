package com.mentis.hrms.workperformance.service;

import com.mentis.hrms.workperformance.dto.ClickUpTaskDTO;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * NEW service. Talks only to ClickUp's API — does not touch any existing
 * HRMS service, repository, or entity.
 *
 * Uses ClickUp's team-wide "Get Filtered Team Tasks" endpoint
 * (GET /team/{team_id}/task) instead of looping per-list. This covers
 * EVERY space/folder/list in the workspace automatically (SEO, Evolve
 * Venture Capital, MDK Sustech, Pulse Digital Media, Edify Services,
 * InyPay, Tanvi Naturals, etc.) with no list IDs to collect or maintain.
 *
 * Free ClickUp plan works fine here: well under the ~100 req/min rate limit
 * even with pagination for a handful of employees per sync run.
 */
@Service
public class ClickUpApiService {

    private static final Logger logger = LoggerFactory.getLogger(ClickUpApiService.class);
    private static final String BASE_URL = "https://api.clickup.com/api/v2";
    private static final int MAX_PAGES = 20; // safety cap: 20 x 100 = 2000 tasks per query, per assignee

    @Value("${clickup.api.token}")
    private String apiToken;

    @Value("${clickup.team.id}")
    private String teamId;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Fetch every task assigned to a given ClickUp user across the ENTIRE
     * workspace, for the given review-month window (epoch millis).
     *
     * Two queries are merged and deduped by task id:
     *  1) Tasks whose DUE DATE falls inside the window — the primary,
     *     accurate "assigned + due this month" set, eligible for on-time scoring.
     *  2) Tasks CREATED inside the window regardless of due date — catches
     *     tasks with no due date set at all, so they still count as
     *     "assigned" work. isCompletedOnTime() only counts on-time/late when
     *     both a due date AND a close date exist, so these never unfairly
     *     count as late — they're simply excluded from that specific metric.
     */
    public List<ClickUpTaskDTO> getTasksForAssignee(String clickupUserId, long startMs, long endMs) {
        Map<String, ClickUpTaskDTO> byId = new LinkedHashMap<>();

        if (clickupUserId == null || clickupUserId.isBlank()) {
            return new ArrayList<>();
        }

        fetchTeamTasks(clickupUserId, "due_date_gt", startMs, "due_date_lt", endMs, byId);
        fetchTeamTasks(clickupUserId, "date_created_gt", startMs, "date_created_lt", endMs, byId);

        return new ArrayList<>(byId.values());
    }

    private void fetchTeamTasks(String clickupUserId, String fromParam, long fromMs,
                                String toParam, long toMs, Map<String, ClickUpTaskDTO> byId) {
        int page = 0;

        while (page < MAX_PAGES) {
            try {
                String url = BASE_URL + "/team/" + teamId + "/task"
                        + "?assignees[]=" + clickupUserId
                        + "&" + fromParam + "=" + fromMs
                        + "&" + toParam + "=" + toMs
                        + "&include_closed=true"
                        + "&subtasks=true"
                        + "&page=" + page;

                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", apiToken);
                HttpEntity<Void> entity = new HttpEntity<>(headers);

                ResponseEntity<String> response =
                        restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

                JSONObject body = new JSONObject(response.getBody());
                JSONArray tasks = body.optJSONArray("tasks");
                if (tasks == null || tasks.length() == 0) break;

                for (int i = 0; i < tasks.length(); i++) {
                    JSONObject t = tasks.getJSONObject(i);
                    String taskId = t.optString("id");
                    if (byId.containsKey(taskId)) continue; // already captured by the other query

                    ClickUpTaskDTO dto = new ClickUpTaskDTO();
                    dto.setId(taskId);
                    dto.setName(t.optString("name"));
                    dto.setAssigneeId(clickupUserId);

                    JSONObject statusObj = t.optJSONObject("status");
                    dto.setStatus(statusObj != null ? statusObj.optString("status") : null);
                    dto.setStatusType(statusObj != null ? statusObj.optString("type") : null);

                    dto.setDueDate(t.isNull("due_date") ? null : Long.parseLong(t.getString("due_date")));
                    dto.setDateClosed(t.isNull("date_closed") ? null : Long.parseLong(t.getString("date_closed")));
                    dto.setTimeEstimate(t.isNull("time_estimate") ? null : t.optLong("time_estimate"));

                    JSONObject priorityObj = t.optJSONObject("priority");
                    dto.setPriority(priorityObj != null ? priorityObj.optString("priority") : "normal");

                    byId.put(taskId, dto);
                }

                if (tasks.length() < 100) break; // last page reached
                page++;

            } catch (Exception e) {
                logger.error("ClickUp team-task API error (page {}) for assignee {}: {}", page, clickupUserId, e.getMessage());
                break;
            }
        }
    }
}
