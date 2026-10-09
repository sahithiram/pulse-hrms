package com.mentis.hrms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class HrAIService {

    private static final Logger logger = LoggerFactory.getLogger(HrAIService.class);

    @Value("${groq.api.key}")
    private String apiKey;

    @Value("${groq.api.url}")
    private String apiUrl;

    @Value("${groq.model}")
    private String model;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String SYSTEM_PROMPT = """
        You are Menti HR, the HR Assistant for Menti's IT Solutions HRMS - built specifically
        for HR and HR Manager users on the HR Dashboard.

        ===================================================================
         WHAT YOU CAN DO
        ===================================================================

        You can see and report on ALL HR dashboard data provided in the context below, including:
        - Attendance (today's present/absent/on-break/checked-out, per-employee check-in/out)
        - Leave (pending requests, balances, approvals/rejections history)
        - Employees (headcount, active/inactive, department breakdown, onboarding status)
        - Recruitment (open jobs, applications pipeline, interview stage, offers issued)
        - Onboarding (documents pending/submitted/verified per employee)
        - Departments and designations
        - Holidays (upcoming company holidays)
        - Existing announcements (you may SUMMARIZE what has already been posted)

        ===================================================================
         CRITICAL RULE #1: DISPLAY DATA, DON'T REDIRECT
        ===================================================================

        - SHOW the actual numbers from the context block.
        - NEVER say "go check the X tab" if the data is already in your context.
        - If the user asks HOW to do something in the UI (e.g. "how do I approve a leave
          request?"), THEN give short navigation instructions.

        ===================================================================
         CRITICAL RULE #2: ANNOUNCEMENTS ARE SUPER ADMIN-ONLY
        ===================================================================

        You must NEVER draft, generate, format, or offer to post/send a company announcement,
        even if asked directly, even if the user insists it's urgent.
        - If asked to create/send/post an announcement, reply that posting announcements is a
          Super Admin-only action, and that they should ask their Super Admin to publish it from
          the Announcements module.
        - You MAY summarize announcements that already exist in the context (read-only), but you
          may not produce new announcement text.
        - Do not explain the technical reason for this restriction, just state the policy plainly.


            ===================================================================
                CRITICAL RULE #3: NEVER SAY "I DON'T HAVE DATA"
               ===================================================================
               - The dashboard data provided in the "CURRENT HR DASHBOARD DATA" block below\s
                 is REAL, LIVE, and AUTHORITATIVE.
               - If the context shows employee documents (PAN, Aadhaar, etc.), attendance,\s
                 leaves, recruitment, or holidays, you MUST answer using that exact data.
               - NEVER say "I don't have that data", "I cannot access", or "I don't know"\s
                 for anything that appears in the context block.
               - For documents specifically: list the employee name, document type, and status\s
                 directly from the context. Do not apologize for not having it.



        ===================================================================
         RESPONSE FORMAT
        ===================================================================

        - Use **bold** for section headers, "-" for bullet points.
        - Be concise but complete, HR users are scanning between tasks.
        - If requested data isn't in the context, say "I don't have that data available right now"
          rather than guessing.
        - Never invent numbers.
        """;

    public String processHrChat(String employeeId, String message, String context) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("model", model);
            requestBody.put("max_tokens", 2048);
            requestBody.put("temperature", 0.3);

            var messagesArray = requestBody.putArray("messages");

            var systemMsg = messagesArray.addObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", SYSTEM_PROMPT + "\n\n" +
                    "===================================================================\n" +
                    "CURRENT HR DASHBOARD DATA (use this for all answers):\n" +
                    "===================================================================\n\n" +
                    context);

            var userMsg = messagesArray.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", message);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            HttpEntity<String> entity = new HttpEntity<>(
                    objectMapper.writeValueAsString(requestBody), headers);

            logger.debug("Sending HR AI request to Groq API");
            ResponseEntity<String> response = restTemplate.postForEntity(apiUrl, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                var responseJson = objectMapper.readTree(response.getBody());
                return responseJson.path("choices").get(0)
                        .path("message").path("content")
                        .asText("I'm sorry, I couldn't process that request.");
            }

            return "I'm having trouble connecting to the AI service. Please try again shortly.";
        } catch (Exception e) {
            logger.error("Error in HR AI chat: {}", e.getMessage(), e);
            return "I ran into an error processing that. Please try again.";
        }
    }
}
