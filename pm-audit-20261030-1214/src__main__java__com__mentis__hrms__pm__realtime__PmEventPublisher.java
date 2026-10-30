package com.mentis.hrms.pm.realtime;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PmEventPublisher {

    private final SimpMessagingTemplate messaging;

    public void publish(String method, String path, String actorEmployeeId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("method", method);
        payload.put("path", path);
        payload.put("actor", actorEmployeeId);
        payload.put("at", System.currentTimeMillis());
        messaging.convertAndSend("/topic/pm/events", payload);
    }
}