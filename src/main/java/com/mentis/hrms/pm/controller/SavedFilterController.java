package com.mentis.hrms.pm.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.entity.SavedFilter;
import com.mentis.hrms.pm.exception.BusinessRuleException;
import com.mentis.hrms.pm.exception.PmAccessDeniedException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.repository.SavedFilterRepository;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.security.PmAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController("pmSavedFilterController")
@RequestMapping("/api/pm/saved-filters")
@RequiredArgsConstructor
public class SavedFilterController {

    public record Body(String name, Long projectId, JsonNode criteria) {}
    public record RenameBody(String name) {}
    public record Item(Long id, String name, Long projectId, JsonNode criteria, boolean isDefault) {}

    private final SavedFilterRepository repo;
    private final PmAccessService access;
    private final ObjectMapper mapper;

    @GetMapping
    public ResponseEntity<ApiResponse<List<Item>>> mine(
            @RequestParam(required = false) Long projectId, @CurrentUserId Long userId) {
        List<Item> out = repo.findByOwnerIdOrderByNameAsc(userId).stream()
                .filter(f -> f.getProjectId() == null || f.getProjectId().equals(projectId))
                .map(this::toItem).collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.ok(out));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<Item>> save(@RequestBody Body b, @CurrentUserId Long userId) {
        String name = b.name() == null ? "" : b.name().trim();
        if (name.isEmpty() || name.length() > 100) throw new BusinessRuleException("Name is required (max 100 characters)");
        if (b.criteria() == null || !b.criteria().isObject()) throw new BusinessRuleException("criteria must be a JSON object");
        String json = b.criteria().toString();
        if (json.length() > 5000) throw new BusinessRuleException("This filter is too large");
        if (b.projectId() != null) access.requireCanViewProject(userId, b.projectId());

        SavedFilter f = repo.findByOwnerIdAndProjectIdAndName(userId, b.projectId(), name)
                .orElseGet(() -> SavedFilter.builder().ownerId(userId).projectId(b.projectId()).name(name).build());
        f.setCriteria(json);
        return ResponseEntity.ok(ApiResponse.ok("Saved", toItem(repo.save(f))));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<Item>> rename(@PathVariable Long id, @RequestBody RenameBody b,
                                                    @CurrentUserId Long userId) {
        SavedFilter f = owned(id, userId);
        String name = b.name() == null ? "" : b.name().trim();
        if (name.isEmpty() || name.length() > 100) throw new BusinessRuleException("Name is required (max 100 characters)");
        repo.findByOwnerIdAndProjectIdAndName(userId, f.getProjectId(), name)
                .filter(x -> !x.getId().equals(id))
                .ifPresent(x -> { throw new BusinessRuleException("You already have a filter with that name"); });
        f.setName(name);
        return ResponseEntity.ok(ApiResponse.ok("Renamed", toItem(repo.save(f))));
    }

    @PostMapping("/{id}/default")
    @Transactional
    public ResponseEntity<ApiResponse<Item>> setDefault(@PathVariable Long id, @CurrentUserId Long userId) {
        SavedFilter f = owned(id, userId);
        for (SavedFilter x : repo.findByOwnerIdAndProjectId(userId, f.getProjectId())) {
            if (x.isDefault() && !x.getId().equals(id)) {
                x.setDefault(false);
                repo.save(x);
            }
        }
        f.setDefault(true);
        return ResponseEntity.ok(ApiResponse.ok("Default set", toItem(repo.save(f))));
    }

    @DeleteMapping("/{id}/default")
    public ResponseEntity<ApiResponse<Item>> clearDefault(@PathVariable Long id, @CurrentUserId Long userId) {
        SavedFilter f = owned(id, userId);
        f.setDefault(false);
        return ResponseEntity.ok(ApiResponse.ok("Default cleared", toItem(repo.save(f))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @CurrentUserId Long userId) {
        SavedFilter f = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Filter not found: " + id));
        if (!Objects.equals(f.getOwnerId(), userId)) throw new PmAccessDeniedException("You can only delete your own filters.");
        repo.delete(f);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }

    private SavedFilter owned(Long id, Long userId) {
        SavedFilter f = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Filter not found: " + id));
        if (!Objects.equals(f.getOwnerId(), userId)) throw new PmAccessDeniedException("You can only change your own filters.");
        return f;
    }

    private Item toItem(SavedFilter f) {
        JsonNode node;
        try {
            node = mapper.readTree(f.getCriteria());
        } catch (Exception e) {
            node = mapper.createObjectNode();
        }
        return new Item(f.getId(), f.getName(), f.getProjectId(), node, f.isDefault());
    }
}