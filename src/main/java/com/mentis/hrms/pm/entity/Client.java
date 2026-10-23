package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "pm_clients", indexes = {
        @Index(name = "idx_pm_client_name", columnList = "name"),
        @Index(name = "idx_pm_client_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "industry", length = 100)
    private String industry;

    @Column(name = "status", length = 20)
    private String status; // active, prospect, hold, archived

    @Column(name = "contact_name", length = 150)
    private String contactName;

    @Column(name = "contact_role", length = 100)
    private String contactRole;

    @Column(name = "email", length = 150)
    private String email;

    @Column(name = "phone", length = 50)
    private String phone;

    @Column(name = "city", length = 100)
    private String city;

    @Column(name = "country", length = 100)
    private String country;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Column(name = "website", length = 200)
    private String website;

    @Column(name = "billing_type", length = 50)
    private String billingType;

    @Column(name = "annual_revenue", precision = 14, scale = 2)
    private BigDecimal annualRevenue;

    @Column(name = "created_by")
    private Long createdBy;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void prePersist() {
        if (status == null) status = "prospect";
    }
}