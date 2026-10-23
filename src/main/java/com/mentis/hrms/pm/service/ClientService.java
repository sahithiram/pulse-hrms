package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.CreateClientRequest;
import com.mentis.hrms.pm.dto.response.ClientResponse;
import com.mentis.hrms.pm.dto.response.ClientSummaryResponse;
import com.mentis.hrms.pm.dto.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface ClientService {
    ClientResponse createClient(CreateClientRequest req, Long userId);
    ClientResponse getClient(Long id);
    PageResponse<ClientSummaryResponse> listClients(String search, String status, Pageable pageable);
    List<ClientSummaryResponse> listAllForDropdown();
    void deleteClient(Long id);
}