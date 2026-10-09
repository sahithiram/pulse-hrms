package com.mentis.hrms.controller;

import com.mentis.hrms.dto.ChatRequestDto;
import com.mentis.hrms.dto.ChatResponseDto;
import com.mentis.hrms.service.ChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    @Autowired
    private ChatService chatService;

    // Send a message (handles both temporary and permanent chats)
    @PostMapping("/send")
    public ResponseEntity<ChatResponseDto> sendMessage(@RequestBody ChatRequestDto request) {
        try {
            ChatResponseDto response = chatService.sendMessage(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error in chat send: {}", e.getMessage(), e);
            ChatResponseDto errorResponse = new ChatResponseDto();
            errorResponse.setSuccess(false);
            errorResponse.setError("Server error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    // Load history for a specific session
    @GetMapping("/session/{sessionId}/history")
    public ResponseEntity<ChatResponseDto> getSessionHistory(
            @PathVariable Long sessionId,
            @RequestParam String employeeId) {
        return ResponseEntity.ok(chatService.loadSessionHistory(sessionId, employeeId));
    }

    // Get all saved sessions for an employee
    @GetMapping("/sessions")
    public ResponseEntity<ChatResponseDto> getAllSessions(@RequestParam String employeeId) {
        return ResponseEntity.ok(chatService.getAllSessions(employeeId));
    }

    // Delete a session
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<ChatResponseDto> deleteSession(
            @PathVariable Long sessionId,
            @RequestParam String employeeId) {
        return ResponseEntity.ok(chatService.deleteSession(sessionId, employeeId));
    }
}
