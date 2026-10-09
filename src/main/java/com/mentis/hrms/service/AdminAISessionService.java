package com.mentis.hrms.service;

import com.mentis.hrms.model.AdminAIChatSession;
import com.mentis.hrms.model.AdminAIChatMessage;
import com.mentis.hrms.repository.AdminAIChatSessionRepository;
import com.mentis.hrms.repository.AdminAIChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class AdminAISessionService {

    private static final Logger logger = LoggerFactory.getLogger(AdminAISessionService.class);

    @Autowired
    private AdminAIChatSessionRepository sessionRepository;

    @Autowired
    private AdminAIChatMessageRepository messageRepository;

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("MMM d, h:mm a");

    /**
     * Create a new admin chat session
     */
    @Transactional
    public Integer createAdminSession(String employeeId, String firstMessage, String firstReply) {
        try {
            // Generate session name from first message
            String sessionName = firstMessage.length() > 40 ?
                    firstMessage.substring(0, 40) + "..." :
                    firstMessage;

            AdminAIChatSession session = new AdminAIChatSession();
            session.setEmployeeId(employeeId);
            session.setSessionName(sessionName);
            session.setCreatedAt(LocalDateTime.now());
            session.setUpdatedAt(LocalDateTime.now());
            session.setMessageCount(0);

            AdminAIChatSession saved = sessionRepository.save(session);

            // Save first user message
            AdminAIChatMessage userMsg = new AdminAIChatMessage();
            userMsg.setSessionId(saved.getId());
            userMsg.setRole("user");
            userMsg.setContent(firstMessage);
            userMsg.setCreatedAt(LocalDateTime.now());
            messageRepository.save(userMsg);

            // Save assistant reply
            AdminAIChatMessage assistantMsg = new AdminAIChatMessage();
            assistantMsg.setSessionId(saved.getId());
            assistantMsg.setRole("assistant");
            assistantMsg.setContent(firstReply);
            assistantMsg.setCreatedAt(LocalDateTime.now());
            messageRepository.save(assistantMsg);

            // Update message count
            saved.setMessageCount(2);
            sessionRepository.save(saved);

            logger.info("Created admin session {} for employee {}", saved.getId(), employeeId);
            return saved.getId();

        } catch (Exception e) {
            logger.error("Error creating admin session: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * Add a message to an existing session
     */
    @Transactional
    public void addMessageToSession(Integer sessionId, String userMessage, String assistantReply) {
        try {
            // Save user message
            AdminAIChatMessage userMsg = new AdminAIChatMessage();
            userMsg.setSessionId(sessionId);
            userMsg.setRole("user");
            userMsg.setContent(userMessage);
            userMsg.setCreatedAt(LocalDateTime.now());
            messageRepository.save(userMsg);

            // Save assistant reply
            AdminAIChatMessage assistantMsg = new AdminAIChatMessage();
            assistantMsg.setSessionId(sessionId);
            assistantMsg.setRole("assistant");
            assistantMsg.setContent(assistantReply);
            assistantMsg.setCreatedAt(LocalDateTime.now());
            messageRepository.save(assistantMsg);

            // Update session
            Optional<AdminAIChatSession> sessionOpt = sessionRepository.findById(sessionId);
            if (sessionOpt.isPresent()) {
                AdminAIChatSession session = sessionOpt.get();
                session.setUpdatedAt(LocalDateTime.now());
                session.setMessageCount(session.getMessageCount() + 2);
                sessionRepository.save(session);
            }

            logger.debug("Added messages to admin session {}", sessionId);

        } catch (Exception e) {
            logger.error("Error adding messages to session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    /**
     * Get session messages for context
     */
    public List<Map<String, String>> getSessionMessages(Integer sessionId) {
        List<Map<String, String>> messages = new ArrayList<>();

        try {
            List<AdminAIChatMessage> dbMessages = messageRepository
                    .findBySessionIdOrderByCreatedAtAsc(sessionId);

            for (AdminAIChatMessage msg : dbMessages) {
                // Limit history to last 10 messages to avoid token limits
                if (messages.size() >= 20) break;

                Map<String, String> map = new HashMap<>();
                map.put("role", msg.getRole());
                map.put("content", msg.getContent());
                messages.add(map);
            }

        } catch (Exception e) {
            logger.error("Error getting session messages: {}", e.getMessage(), e);
        }

        return messages;
    }

    /**
     * Get admin sessions for an employee
     */
    public List<Map<String, Object>> getAdminSessions(String employeeId) {
        List<Map<String, Object>> result = new ArrayList<>();

        try {
            List<AdminAIChatSession> sessions = sessionRepository
                    .findByEmployeeIdOrderByUpdatedAtDesc(employeeId);

            for (AdminAIChatSession session : sessions) {
                Map<String, Object> map = new HashMap<>();
                map.put("id", session.getId());
                map.put("sessionName", session.getSessionName());
                map.put("updatedAt", session.getUpdatedAt().format(DATE_FORMATTER));
                map.put("messageCount", session.getMessageCount());
                result.add(map);
            }

        } catch (Exception e) {
            logger.error("Error getting admin sessions: {}", e.getMessage(), e);
        }

        return result;
    }

    /**
     * Get session history with messages
     */
    public Map<String, Object> getAdminSessionHistory(Integer sessionId, String employeeId) {
        Map<String, Object> result = new HashMap<>();

        try {
            Optional<AdminAIChatSession> sessionOpt = sessionRepository.findById(sessionId);
            if (sessionOpt.isEmpty()) {
                result.put("success", false);
                result.put("error", "Session not found");
                return result;
            }

            AdminAIChatSession session = sessionOpt.get();

            // Verify ownership
            if (!session.getEmployeeId().equals(employeeId)) {
                result.put("success", false);
                result.put("error", "Access denied");
                return result;
            }

            List<AdminAIChatMessage> messages = messageRepository
                    .findBySessionIdOrderByCreatedAtAsc(sessionId);

            List<Map<String, String>> messageList = new ArrayList<>();
            for (AdminAIChatMessage msg : messages) {
                Map<String, String> map = new HashMap<>();
                map.put("role", msg.getRole());
                map.put("content", msg.getContent());
                messageList.add(map);
            }

            result.put("success", true);
            result.put("sessionName", session.getSessionName());
            result.put("messages", messageList);

        } catch (Exception e) {
            logger.error("Error getting session history: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Delete a session
     */
    @Transactional
    public boolean deleteAdminSession(Integer sessionId, String employeeId) {
        try {
            Optional<AdminAIChatSession> sessionOpt = sessionRepository.findById(sessionId);
            if (sessionOpt.isEmpty()) {
                return false;
            }

            AdminAIChatSession session = sessionOpt.get();

            // Verify ownership
            if (!session.getEmployeeId().equals(employeeId)) {
                logger.warn("Unauthorized delete attempt for session {} by employee {}", sessionId, employeeId);
                return false;
            }

            // Delete messages first
            messageRepository.deleteBySessionId(sessionId);

            // Delete session
            sessionRepository.deleteById(sessionId);

            logger.info("Deleted admin session {} for employee {}", sessionId, employeeId);
            return true;

        } catch (Exception e) {
            logger.error("Error deleting session {}: {}", sessionId, e.getMessage(), e);
            return false;
        }
    }
}
