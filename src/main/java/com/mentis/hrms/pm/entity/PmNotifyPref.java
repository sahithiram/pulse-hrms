package com.mentis.hrms.pm.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "pm_notify_pref")
@Getter @Setter @NoArgsConstructor
public class PmNotifyPref {
    @Id @Column(name = "employee_id") private Long employeeId;
    @Column(name = "email_handover", nullable = false) private boolean emailHandover = true;
    @Column(name = "email_high_only", nullable = false) private boolean emailHighOnly = true;
    @UpdateTimestamp @Column(name = "updated_at") private LocalDateTime updatedAt;
}