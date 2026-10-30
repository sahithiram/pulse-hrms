package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.entity.PmNotifyPref;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.service.PmNotifyPrefService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController("pmNotifySettingsController")
@RequestMapping("/api/pm/settings/notify")
@RequiredArgsConstructor
public class PmNotifySettingsController {

    public record Body(Boolean emailHandover, Boolean emailHighOnly) {}

    private final PmNotifyPrefService service;

    @Value("${pm.notify.email.enabled:true}") private boolean globalEnabled;

    private Map<String, Object> view(PmNotifyPref p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("emailHandover", p.isEmailHandover());
        m.put("emailHighOnly", p.isEmailHighOnly());
        m.put("globalEnabled", globalEnabled);
        return m;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> get(@CurrentUserId Long userId) {
        return ResponseEntity.ok(ApiResponse.ok(view(service.get(userId))));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> put(@RequestBody Body b, @CurrentUserId Long userId) {
        PmNotifyPref cur = service.get(userId);
        PmNotifyPref p = service.save(userId,
                b.emailHandover() == null ? cur.isEmailHandover() : b.emailHandover(),
                b.emailHighOnly() == null ? cur.isEmailHighOnly() : b.emailHighOnly());
        return ResponseEntity.ok(ApiResponse.ok("Saved", view(p)));
    }
}