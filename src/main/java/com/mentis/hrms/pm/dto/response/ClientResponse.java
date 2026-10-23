package com.mentis.hrms.pm.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClientResponse {
    private Long id;
    private String name;
    private String industry;
    private String status;
    private String contactName;
    private String contactRole;
    private String email;
    private String phone;
    private String city;
    private String country;
    private String address;
    private Double latitude;
    private Double longitude;
    private String website;
    private String billingType;
    private BigDecimal annualRevenue;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}