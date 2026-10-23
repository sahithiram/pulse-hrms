package com.mentis.hrms.service;

import com.mentis.hrms.model.HrAIChatSession;
import com.mentis.hrms.model.HrAIChatMessage;
import com.mentis.hrms.repository.HrAIChatSessionRepository;
import com.mentis.hrms.repository.HrAIChatMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class HrAISessionService {

    private static final Logger logger = LoggerFactory.getLogger(HrAISessionService.class);

    @Autowired
    private HrAIChatSessionRepository sessionRepository;

    @Autowired
    private HrAIChatMessageRepository messageRepository;

    @Transactional
    public Integer createHrSession(String employeeId, String firstMessage, String firstReply) {
        try {
            String sessionName = firstMessage.length() > 40
                    ? firstMessage.substring(0, 40) + "..." : firstMessage;

            HrAIChatSession session = new HrAIChatSession();
            session.setEmployeeId(employeeId);
            session.setSessionName(sessionName);
            session.setCreatedAt(LocalDateTime.now());
            session.setUpdatedAt(LocalDateTime.now());
            session.setMessageCount(0);

            HrAIChatSession saved = sessionRepository.save(session);

            saveMessage(saved.getId(), "user", firstMessage);
            saveMessage(saved.getId(), "assistant", firstReply);

            saved.setMessageCount(2);
            sessionRepository.save(saved);

            logger.info("Created HR AI session {} for {}", saved.getId(), employeeId);
            return saved.getId();
        } catch (Exception e) {
            logger.error("Error creating HR AI session: {}", e.getMessage(), e);
            return null;
        }
    }

    @Transactional
    public void addMessageToSession(Integer sessionId, String userMessage, String assistantReply) {
        try {
            saveMessage(sessionId, "user", userMessage);
            saveMessage(sessionId, "assistant", assistantReply);

            sessionRepository.findById(sessionId).ifPresent(session -> {
                session.setUpdatedAt(LocalDateTime.now());
                session.setMessageCount(session.getMessageCount() + 2);
                sessionRepository.save(session);
            });
        } catch (Exception e) {
            logger.error("Error adding messages to HR session {}: {}", sessionId, e.getMessage(), e);
        }
    }

    private void saveMessage(Integer sessionId, String role, String content) {
        HrAIChatMessage msg = new HrAIChatMessage();
        msg.setSessionId(sessionId);
        msg.setRole(role);
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        messageRepository.save(msg);
    }

    public List<Map<String, String>> getSessionMessages(Integer sessionId) {
        List<Map<String, String>> messages = new ArrayList<>();
        try {
            List<HrAIChatMessage> dbMessages = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
            for (HrAIChatMessage msg : dbMessages) {
                if (messages.size() >= 20) break; // cap context sent back to model
                Map<String, String> map = new HashMap<>();
                map.put("role", msg.getRole());
                map.put("content", msg.getContent());
                messages.add(map);
            }
        } catch (Exception e) {
            logger.error("Error getting HR session messages: {}", e.getMessage(), e);
        }
        return messages;
    }

    public List<Map<String, Object>> getHrSessions(String employeeId) {
        List<Map<String, Object>> result = new ArrayList<>();
        try {
            List<HrAIChatSession> sessions = sessionRepository.findByEmployeeIdOrderByUpdatedAtDesc(employeeId);
            for (HrAIChatSession s : sessions) {
                Map<String, Object> m = new HashMap<>();
                m.put("id", s.getId());
                m.put("sessionName", s.getSessionName());
                m.put("messageCount", s.getMessageCount());
                m.put("updatedAt", s.getUpdatedAt());
                result.add(m);
            }
        } catch (Exception e) {
            logger.error("Error listing HR sessions: {}", e.getMessage(), e);
        }
        return result;
    }
}
