package com.mentis.hrms.pm.controller;

import com.mentis.hrms.pm.dto.request.CreateClientRequest;
import com.mentis.hrms.pm.dto.response.ApiResponse;
import com.mentis.hrms.pm.dto.response.ClientResponse;
import com.mentis.hrms.pm.dto.response.ClientSummaryResponse;
import com.mentis.hrms.pm.dto.response.PageResponse;
import com.mentis.hrms.pm.security.CurrentUserId;
import com.mentis.hrms.pm.service.ClientService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.mentis.hrms.pm.security.PmAccessService;
import java.util.List;

@RestController("pmClientController")
@RequestMapping("/api/pm/clients")
@RequiredArgsConstructor
public class ClientController {

    private final ClientService clientService;
    private final PmAccessService access;

    @PostMapping
    public ResponseEntity<ApiResponse<ClientResponse>> create(
            @Valid @RequestBody CreateClientRequest req,
            @CurrentUserId Long userId) {
        access.requireAdmin(userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Client created", clientService.createClient(req, userId)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ClientResponse>> get(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok(clientService.getClient(id)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ClientSummaryResponse>>> list(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.ASC, "name"));
        return ResponseEntity.ok(ApiResponse.ok(clientService.listClients(search, status, pageable)));
    }

    @GetMapping("/dropdown")
    public ResponseEntity<ApiResponse<List<ClientSummaryResponse>>> dropdown() {
        return ResponseEntity.ok(ApiResponse.ok(clientService.listAllForDropdown()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id, @CurrentUserId Long userId) {
        access.requireCanCreateProject(userId);
        clientService.deleteClient(id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted", null));
    }
}