package com.mentis.hrms.pm.mapper;

import com.mentis.hrms.pm.dto.request.CreateClientRequest;
import com.mentis.hrms.pm.dto.response.ClientResponse;
import com.mentis.hrms.pm.dto.response.ClientSummaryResponse;
import com.mentis.hrms.pm.entity.Client;
import org.springframework.stereotype.Component;

@Component
public class ClientMapper {

    public Client toEntity(CreateClientRequest req, Long createdBy) {
        return Client.builder()
                .name(req.getName())
                .industry(req.getIndustry())
                .status(req.getStatus() != null ? req.getStatus() : "prospect")
                .contactName(req.getContactName())
                .contactRole(req.getContactRole())
                .email(req.getEmail())
                .phone(req.getPhone())
                .city(req.getCity())
                .country(req.getCountry())
                .address(req.getAddress())
                .latitude(req.getLatitude())
                .longitude(req.getLongitude())
                .website(req.getWebsite())
                .billingType(req.getBillingType())
                .annualRevenue(req.getAnnualRevenue())
                .createdBy(createdBy)
                .build();
    }

    public ClientResponse toResponse(Client c) {
        return ClientResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .industry(c.getIndustry())
                .status(c.getStatus())
                .contactName(c.getContactName())
                .contactRole(c.getContactRole())
                .email(c.getEmail())
                .phone(c.getPhone())
                .city(c.getCity())
                .country(c.getCountry())
                .address(c.getAddress())
                .latitude(c.getLatitude())
                .longitude(c.getLongitude())
                .website(c.getWebsite())
                .billingType(c.getBillingType())
                .annualRevenue(c.getAnnualRevenue())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    public ClientSummaryResponse toSummary(Client c) {
        return ClientSummaryResponse.builder()
                .id(c.getId())
                .name(c.getName())
                .industry(c.getIndustry())
                .status(c.getStatus())
                .build();
    }
}