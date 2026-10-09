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

import java.util.List;

@Service
public class OpenRouterService {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterService.class);

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
    You are Menti, the HR Assistant for Menti's IT Solutions HRMS.

    ═══════════════════════════════════════════════════════════════
    CRITICAL RULE #1: DISPLAY DATA, DON'T GIVE INSTRUCTIONS
    ═══════════════════════════════════════════════════════════════

    When the user asks for ANY summary (attendance, leave, etc.):
    - ✅ SHOW the actual numbers from the data block
    - ❌ NEVER say "go to the X tab" or "click on Y page"
    - ❌ NEVER say "you can see this on the dashboard"
    - ✅ If data exists, PRESENT IT directly

    ═══════════════════════════════════════════════════════════════
    HOW TO RESPOND TO ATTENDANCE QUESTIONS
    ═══════════════════════════════════════════════════════════════

    When asked "I want my attendance summary" or similar:

    1. Look at the "MONTHLY ATTENDANCE SUMMARY" section
    2. Look at the "RECENT 7-DAY ATTENDANCE LOG" section
    3. Format your response like this:

    **📊 Your Attendance Summary**

    **This Month (June 2026):**
    - Present days: [number] out of [total]
    - Absent days: [number]
    - Leave days: [number]
    - Attendance: [percentage]%
    - Total hours worked: [hours]

    **Recent Activity:**
    - [Date]: [Status] (In: [time], Out: [time])
    - [Date]: [Status] (In: [time], Out: [time])

    **Today:**
    - Status: [Active/Checked Out/Not checked in]
    - Check-in: [time] | Check-out: [time]

    ═══════════════════════════════════════════════════════════════
    LEAVE SUMMARY RESPONSE FORMAT
    ═══════════════════════════════════════════════════════════════

    When asked about leave balance:

    **Your Leave Balance**
    - Sick Leave: [used]/[total] used, [remaining] remaining
    - Casual Leave: [used]/[total] used, [remaining] remaining
    - Earned Leave: [used] used, [remaining] remaining
    - Pending requests: [number]

    ═══════════════════════════════════════════════════════════════
    OTHER RULES
    ═══════════════════════════════════════════════════════════════

    - If data is missing, say "I don't have that data available" — don't redirect
    - If the user asks HOW to do something (e.g., "how do I apply for leave?"), THEN give instructions
    - Be concise but complete
    - Use **bold** for section headers
    - Use - for bullet points

    ═══════════════════════════════════════════════════════════════
    COMPANY POLICIES
    ═══════════════════════════════════════════════════════════════

    - Working Hours: 9 AM-6 PM, 8 hours/day, Monday-Friday
    - Leave: minimum 2 days' notice, manager approval required
    - Sick Leave: 12 days/year
    - Casual Leave: 7 days/year
    - Earned Leave: accrues with no fixed cap
    - Check-in after 9:15 AM is marked late
    - Mandatory documents must be uploaded before deadline

    ═══════════════════════════════════════════════════════════════
    REMEMBER: DISPLAY DATA. NEVER REDIRECT.
    ═══════════════════════════════════════════════════════════════
    """;

    public String chat(String userMessage, List<MessagePair> conversationHistory, String employeeContext) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("max_tokens", 1536);
            requestBody.put("temperature", 0.35);

            ArrayNode messages = requestBody.putArray("messages");

            ObjectNode systemMsg = messages.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", SYSTEM_PROMPT + "\n\nHere is the employee's live data for this conversation:\n\n" + employeeContext);

            for (MessagePair pair : conversationHistory) {
                ObjectNode userNode = messages.addObject();
                userNode.put("role", "user");
                userNode.put("content", pair.getUserMessage());

                ObjectNode assistantNode = messages.addObject();
                assistantNode.put("role", "assistant");
                assistantNode.put("content", pair.getAssistantMessage());
            }

            ObjectNode currentMsg = messages.addObject();
            currentMsg.put("role", "user");
            currentMsg.put("content", userMessage);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<String> entity = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);

            log.debug("Sending Groq request with context length: {}", employeeContext.length());
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode responseJson = objectMapper.readTree(response.getBody());
                String reply = responseJson.path("choices").get(0)
                        .path("message").path("content")
                        .asText("Sorry, I couldn't process that right now.");
                log.debug("Groq response received successfully");
                return reply;
            }

            return "I'm having trouble connecting to the AI service. Please try again shortly.";
        } catch (HttpStatusCodeException e) {
            log.error("Groq API rejected the request — status {} body: {}", e.getStatusCode(), e.getResponseBodyAsString());
            return "Technical issue occurred. Please try again in a moment.";
        } catch (Exception e) {
            log.error("Groq API error: {}", e.getMessage(), e);
            return "Technical issue occurred. Please try again in a moment.";
        }
    }

    public static class MessagePair {
        private final String userMessage;
        private final String assistantMessage;

        public MessagePair(String userMessage, String assistantMessage) {
            this.userMessage = userMessage;
            this.assistantMessage = assistantMessage;
        }

        public String getUserMessage() { return userMessage; }
        public String getAssistantMessage() { return assistantMessage; }
    }
}
