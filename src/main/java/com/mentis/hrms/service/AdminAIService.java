package com.mentis.hrms.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class AdminAIService {

    private static final Logger logger = LoggerFactory.getLogger(AdminAIService.class);

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        You are Menti Admin, the Super Admin AI Assistant for Menti's IT Solutions HRMS.
        The Super Admin has FULL, UNRESTRICTED access to every module in this system.
        You must answer any question using the data provided in the context blocks below
        — never say "not mentioned" or redirect the user to a page/tab if the data exists
        anywhere in the context.

        YOU WILL RECEIVE UP TO TWO CONTEXT BLOCKS, EACH WITH A DIFFERENT JOB:

        1. "ORGANIZATION-WIDE DATA" — live, structured facts pulled straight from the
           database right now (employees, attendance, leave requests, jobs, locations,
           announcements, holidays, roles, etc.). Use this block for any question about
           WHAT IS TRUE RIGHT NOW — counts, records, statuses, names, dates, IDs.

        2. "POLICY & CAPABILITY KNOWLEDGE" — a handful of passages retrieved from the
           company's policy documents and from this assistant's own skills/capability
           documentation, based on semantic relevance to the user's question. Use this
           block for any question about RULES, POLICY, ENTITLEMENT LOGIC, or WHAT THIS
           ASSISTANT CAN DO (e.g. "how many sick days do we allow", "what's the
           carry-forward rule for casual leave", "can you generate an attendance
           report"). If this block is empty or doesn't cover the question, say plainly
           that the policy isn't available rather than inventing a rule.

        Never mix the two: do not answer a policy question by guessing from live data,
        and do not answer a live-data question by quoting policy text.

        SKILLS YOU HAVE (backed by live data and/or retrieved policy context):
        - Attendance & check-in/check-out (daily, monthly, per-employee)
        - Leave management (requests, balances, approvals, history, policy rules)
        - Employee directory & full employee management
        - Departments, designations, and org structure
        - Roles & permissions
        - Recruitment (jobs, applications, offer letters, hiring pipeline)
        - Onboarding documents (status, verification, deadlines)
        - Announcements
        - Holidays & company calendar
        - Locations/offices

        You ALSO have TOOLS available to actually perform actions (approve/reject leave,
        create announcements, manage locations, generate reports). You do NOT need an
        exact ID — if the user gives an employee name and/or leave type instead of a
        leave ID, pass whatever details you have (employeeName, leaveType, etc.) and the
        backend will resolve the correct record.

        HOW TO DECIDE BETWEEN ANSWERING AND CALLING A TOOL — judge by MEANING, not by
        matching specific words. The user will phrase things naturally and won't use any
        fixed vocabulary, so do not pattern-match on exact verbs like "download" or
        "approve". Instead ask yourself:
          - Is the user asking me to TELL them something (a fact, a count, a list, an
            explanation, a policy rule)? -> Answer directly from context. Do NOT call a
            tool, even if the message contains a word like "report" or "leave" — wanting
            information about something is not the same as wanting an action performed
            on it.
          - Is the user asking me to CHANGE something or PRODUCE a downloadable file
            (approve/reject a request, create/edit/remove a record, generate an actual
            document to download)? -> Call the matching tool.
          - If it's genuinely ambiguous whether they want information or a file/action,
            default to answering the question in text and mention that you can also
            produce/perform it if they'd like — do not silently take an action the user
            may not have asked for.

        RULES:
        1. ALWAYS answer using the data in the context blocks — never invent numbers or
           policy rules that aren't present.
        2. Search the ENTIRE context, including the "ORGANIZATION-WIDE DATA" and
           "POLICY & CAPABILITY KNOWLEDGE" sections, before saying something isn't
           available.
        3. If a data point or policy truly isn't in the context after checking
           everything, say so plainly — do not tell the Super Admin to "check the
           dashboard."
        4. Give exact, complete, pin-to-pin detail when asked (names, IDs, dates,
           amounts, statuses) — the Super Admin is authorized to see everything.
        5. Be concise but complete. Use **bold** headers and bullet points.
        6. Only call a tool when the user is clearly asking you to perform an action or
           produce a downloadable file, per the guidance above. Otherwise, respond with
           a normal answer.
        """;

    public static class AdminAIResult {
        public boolean isToolCall;
        public String toolCallId;
        public String toolName;
        public JsonNode toolArgs;
        public String textReply;
    }

    public AdminAIResult processAdminChat(String employeeId, String message, String context, String ragContext) {
        ObjectNode requestBody = buildBaseRequest(context, ragContext);
        var messagesArray = (ArrayNode) requestBody.get("messages");

        var userMsg = messagesArray.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", message);

        requestBody.set("tools", buildToolDefinitions());
        requestBody.put("tool_choice", "auto");

        return callGroq(requestBody);
    }

    public AdminAIResult continueAfterToolResult(String message, String context, String ragContext,
                                                 String toolCallId, String toolName,
                                                 JsonNode toolArgs, String toolResultText) {
        ObjectNode requestBody = buildBaseRequest(context, ragContext);
        var messagesArray = (ArrayNode) requestBody.get("messages");

        var userMsg = messagesArray.addObject();
        userMsg.put("role", "user");
        userMsg.put("content", message);

        var assistantMsg = messagesArray.addObject();
        assistantMsg.put("role", "assistant");
        assistantMsg.putNull("content");
        var toolCallsArr = assistantMsg.putArray("tool_calls");
        var toolCallNode = toolCallsArr.addObject();
        toolCallNode.put("id", toolCallId);
        toolCallNode.put("type", "function");
        var fnNode = toolCallNode.putObject("function");
        fnNode.put("name", toolName);
        fnNode.put("arguments", toolArgs.toString());

        var toolMsg = messagesArray.addObject();
        toolMsg.put("role", "tool");
        toolMsg.put("tool_call_id", toolCallId);
        toolMsg.put("content", toolResultText);

        return callGroq(requestBody);
    }

    private ObjectNode buildBaseRequest(String context, String ragContext) {
        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("model", model);
        requestBody.put("max_tokens", 2048);
        requestBody.put("temperature", 0.3);

        var messagesArray = requestBody.putArray("messages");
        var systemMsg = messagesArray.addObject();

        StringBuilder fullSystemContent = new StringBuilder();
        fullSystemContent.append(SYSTEM_PROMPT).append("\n\n");

        fullSystemContent.append("═══════════════════════════════════════════════════════════════\n")
                .append("ORGANIZATION-WIDE DATA (live facts — use for \"what is true right now\"):\n")
                .append("═══════════════════════════════════════════════════════════════\n\n")
                .append(context != null ? context : "(no live context available)")
                .append("\n\n");

        fullSystemContent.append("═══════════════════════════════════════════════════════════════\n")
                .append("POLICY & CAPABILITY KNOWLEDGE (retrieved for this question — use for rules/policy/what-I-can-do):\n")
                .append("═══════════════════════════════════════════════════════════════\n\n")
                .append((ragContext != null && !ragContext.isBlank())
                        ? ragContext
                        : "(no relevant policy passages retrieved for this question)");

        systemMsg.put("role", "system");
        systemMsg.put("content", fullSystemContent.toString());
        return requestBody;
    }

    private AdminAIResult callGroq(ObjectNode requestBody) {
        AdminAIResult result = new AdminAIResult();
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
            logger.debug("Sending admin request to Groq API");
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                var responseJson = objectMapper.readTree(response.getBody());
                var message = responseJson.path("choices").get(0).path("message");
                var toolCalls = message.path("tool_calls");

                if (toolCalls.isArray() && toolCalls.size() > 0) {
                    var call = toolCalls.get(0);
                    result.isToolCall = true;
                    result.toolCallId = call.path("id").asText();
                    result.toolName = call.path("function").path("name").asText();
                    String argsJson = call.path("function").path("arguments").asText("{}");
                    result.toolArgs = objectMapper.readTree(argsJson);
                    return result;
                }

                result.isToolCall = false;
                result.textReply = message.path("content").asText("I'm sorry, I couldn't process that request.");
                return result;
            }

            result.isToolCall = false;
            result.textReply = "I'm having trouble connecting to the AI service. Please try again.";
            return result;

        } catch (HttpStatusCodeException e) {
            logger.error("Groq API error: {} - {}", e.getStatusCode(), e.getResponseBodyAsString());
            result.isToolCall = false;
            result.textReply = "Technical issue occurred. Please try again.";
            return result;
        } catch (Exception e) {
            logger.error("Error in admin AI chat: {}", e.getMessage(), e);
            result.isToolCall = false;
            result.textReply = "An error occurred. Please try again.";
            return result;
        }
    }

    private ArrayNode buildToolDefinitions() {
        ArrayNode tools = objectMapper.createArrayNode();

        tools.add(buildTool("approve_leave", "Approve a pending leave request. Give a leaveId if known, " +
                        "otherwise give employeeName and/or leaveType and the backend will find the matching request.",
                Map.of("leaveId", "e.g. LV-2026-1234, if known",
                        "employeeName", "employee's name, if leaveId not known",
                        "leaveType", "sick, casual, or earned, if known",
                        "remarks", "optional approval remarks"),
                List.of()));

        tools.add(buildTool("reject_leave", "Reject a pending leave request. Give a leaveId if known, " +
                        "otherwise give employeeName and/or leaveType.",
                Map.of("leaveId", "e.g. LV-2026-1234, if known",
                        "employeeName", "employee's name, if leaveId not known",
                        "leaveType", "sick, casual, or earned, if known",
                        "reason", "reason for rejection"),
                List.of()));

        tools.add(buildTool("create_announcement", "Post a new company-wide announcement",
                Map.of("title", "The announcement title", "description", "The announcement body"),
                List.of("title", "description")));

        tools.add(buildTool("add_location", "Add a new office location",
                Map.of("name", "Location name", "type", "Location type", "country", "Country",
                        "state", "State", "city", "City", "address", "Street address",
                        "phone", "Phone number", "email", "Email address"),
                List.of("name", "city")));

        tools.add(buildTool("update_location", "Update an existing office location",
                Map.of("id", "The location ID", "field", "Which field to change", "value", "New value"),
                List.of("id", "field", "value")));

        tools.add(buildTool("delete_location", "Delete an office location",
                Map.of("id", "The location ID to delete"), List.of("id")));

        tools.add(buildTool("generate_report", "Generate a downloadable report",
                Map.of("reportType", "employees, locations, jobs, or departments",
                        "format", "pdf, excel, csv, or word"),
                List.of("reportType", "format")));

        return tools;
    }

    private ObjectNode buildTool(String name, String description, Map<String, String> params, List<String> required) {
        ObjectNode tool = objectMapper.createObjectNode();
        tool.put("type", "function");
        ObjectNode function = objectMapper.createObjectNode();
        function.put("name", name);
        function.put("description", description);
        ObjectNode parameters = objectMapper.createObjectNode();
        parameters.put("type", "object");
        ObjectNode properties = objectMapper.createObjectNode();
        for (var entry : params.entrySet()) {
            ObjectNode prop = objectMapper.createObjectNode();
            prop.put("type", "string");
            prop.put("description", entry.getValue());
            properties.set(entry.getKey(), prop);
        }
        parameters.set("properties", properties);
        parameters.set("required", objectMapper.valueToTree(required));
        function.set("parameters", parameters);
        tool.set("function", function);
        return tool;
    }
}
