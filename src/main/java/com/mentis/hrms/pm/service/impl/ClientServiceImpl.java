package com.mentis.hrms.pm.service.impl;

import com.mentis.hrms.pm.dto.request.CreateClientRequest;
import com.mentis.hrms.pm.dto.response.ClientResponse;
import com.mentis.hrms.pm.dto.response.ClientSummaryResponse;
import com.mentis.hrms.pm.dto.response.PageResponse;
import com.mentis.hrms.pm.entity.Client;
import com.mentis.hrms.pm.exception.DuplicateResourceException;
import com.mentis.hrms.pm.exception.ResourceNotFoundException;
import com.mentis.hrms.pm.mapper.ClientMapper;
import com.mentis.hrms.pm.repository.ClientRepository;
import com.mentis.hrms.pm.service.ClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ClientServiceImpl implements ClientService {

    private final ClientRepository clientRepository;
    private final ClientMapper clientMapper;

    @Override
    @Transactional
    public ClientResponse createClient(CreateClientRequest req, Long userId) {
        if (clientRepository.existsByName(req.getName())) {
            throw new DuplicateResourceException("Client name already exists: " + req.getName());
        }
        Client saved = clientRepository.save(clientMapper.toEntity(req, userId));
        log.info("Client created id={} name={}", saved.getId(), saved.getName());
        return clientMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ClientResponse getClient(Long id) {
        return clientMapper.toResponse(clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Client not found: " + id)));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ClientSummaryResponse> listClients(String search, String status, Pageable pageable) {
        Page<Client> page = clientRepository.search(search, status, pageable);
        return PageResponse.of(page.map(clientMapper::toSummary));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ClientSummaryResponse> listAllForDropdown() {
        return clientRepository.findAll().stream()
                .map(clientMapper::toSummary)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteClient(Long id) {
        if (!clientRepository.existsById(id)) {
            throw new ResourceNotFoundException("Client not found: " + id);
        }
        clientRepository.deleteById(id);
    }
}