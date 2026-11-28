package com.mentis.hrms.model;

import jakarta.persistence.*;

@Entity
@Table(name = "onboarding_template_task")
public class OnboardingTemplateTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "owner_role", nullable = false, length = 30)
    private String ownerRole;

    @Column(name = "due_offset_days", nullable = false)
    private int dueOffsetDays = 0;

    @Column(name = "mandatory", nullable = false)
    private boolean mandatory = true;

    @Column(name = "position", nullable = false)
    private int position = 0;

    public Long getId() { return id; }
    public Long getTemplateId() { return templateId; }
    public void setTemplateId(Long templateId) { this.templateId = templateId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getOwnerRole() { return ownerRole; }
    public void setOwnerRole(String ownerRole) { this.ownerRole = ownerRole; }
    public int getDueOffsetDays() { return dueOffsetDays; }
    public void setDueOffsetDays(int dueOffsetDays) { this.dueOffsetDays = dueOffsetDays; }
    public boolean isMandatory() { return mandatory; }
    public void setMandatory(boolean mandatory) { this.mandatory = mandatory; }
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
}
