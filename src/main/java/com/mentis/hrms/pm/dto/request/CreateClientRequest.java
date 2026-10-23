package com.mentis.hrms.pm.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.math.BigDecimal;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class CreateClientRequest {

    @NotBlank @Size(max = 200)
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
}