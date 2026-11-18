package com.mentis.hrms.pm.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mentis.hrms.pm.dto.request.CreateTemplateRequest;
import com.mentis.hrms.pm.enums.TemplateType;
import com.mentis.hrms.pm.repository.ProjectTemplateRepository;
import com.mentis.hrms.pm.service.ProjectTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Adds a few ready-made project templates on startup so "Start from a template" is never empty.
 * Safe to run every time: a template is only added when no template with that name exists, and every
 * payload goes through the normal template validation. Turn off with:  pm.seed.templates=false
 */
@Component
@Order(110)
@Slf4j
@RequiredArgsConstructor
public class DefaultTemplateSeeder implements CommandLineRunner {

    private final ProjectTemplateRepository repository;
    private final ProjectTemplateService templateService;
    private final ObjectMapper objectMapper;

    @Value("${pm.seed.templates:true}")
    private boolean enabled;

    @Override
    public void run(String... args) {
        if (!enabled) return;
        seed("Scrum - Software Delivery", TemplateType.SCRUM,
                "Four 2-week sprints with requirements, build, testing and release. Good for software products.", SCRUM);
        seed("Kanban - Support and Maintenance", TemplateType.KANBAN,
                "A continuous board for support work: triage, priority tickets, fixes and a monthly review.", KANBAN);
        seed("Waterfall - Classic Delivery", TemplateType.WATERFALL,
                "Requirements, design, build, testing and deployment in order, with sign-off milestones.", WATERFALL);
        seed("Marketing Campaign", TemplateType.HYBRID,
                "Plan, create, launch and review a campaign over about five weeks. For non-IT teams.", MARKETING);
        seed("Operations Rollout", TemplateType.HYBRID,
                "Prepare, pilot, roll out and review a new process or office change.", OPERATIONS);
    }

    private void seed(String name, TemplateType type, String description, String payloadJson) {
        try {
            if (repository.existsByNameIgnoreCase(name)) return;
            templateService.create(new CreateTemplateRequest(name, type, description,
                    objectMapper.readTree(payloadJson)), null);
            log.info("Added starter project template: {}", name);
        } catch (Exception e) {
            log.warn("Could not add starter template '{}': {}", name, e.getMessage());
        }
    }

    // Offsets are days from the project start date. assignToRole: MANAGER = project owner, MEMBER = shared among members.

    private static final String SCRUM = """
        {"project":{"name":"Software Project","priority":"MEDIUM"},
         "milestones":[{"name":"Requirements agreed","dueOffsetDays":7},{"name":"MVP ready","dueOffsetDays":42},{"name":"Release","dueOffsetDays":56}],
         "sprints":[
           {"name":"Sprint 1","goal":"Set up and foundations","startOffsetDays":0,"durationDays":14},
           {"name":"Sprint 2","goal":"Core features","startOffsetDays":14,"durationDays":14},
           {"name":"Sprint 3","goal":"Complete features and integrate","startOffsetDays":28,"durationDays":14},
           {"name":"Sprint 4","goal":"Test, fix and release","startOffsetDays":42,"durationDays":14}],
         "tasks":[
           {"title":"Define scope and requirements","priority":"HIGH","type":"STORY","assignToRole":"MANAGER","estimateHours":8,"storyPoints":3,"sprintIndex":0,"milestoneIndex":0},
           {"title":"Set up repository, CI and environments","priority":"MEDIUM","type":"TASK","assignToRole":"MEMBER","estimateHours":8,"storyPoints":3,"sprintIndex":0},
           {"title":"Design system architecture","priority":"HIGH","type":"TASK","assignToRole":"MEMBER","estimateHours":12,"storyPoints":5,"sprintIndex":0},
           {"title":"Build core features","priority":"HIGH","type":"FEATURE","assignToRole":"MEMBER","estimateHours":40,"storyPoints":8,"sprintIndex":1},
           {"title":"Implement login and user roles","priority":"HIGH","type":"FEATURE","assignToRole":"MEMBER","estimateHours":24,"storyPoints":5,"sprintIndex":1},
           {"title":"Build user-facing screens","priority":"MEDIUM","type":"FEATURE","assignToRole":"MEMBER","estimateHours":40,"storyPoints":8,"sprintIndex":2,"milestoneIndex":1},
           {"title":"Integrate external services","priority":"MEDIUM","type":"TASK","assignToRole":"MEMBER","estimateHours":16,"storyPoints":3,"sprintIndex":2},
           {"title":"Write automated tests","priority":"MEDIUM","type":"TASK","assignToRole":"MEMBER","estimateHours":24,"storyPoints":5,"sprintIndex":2},
           {"title":"Fix bugs and harden","priority":"MEDIUM","type":"BUG","assignToRole":"MEMBER","estimateHours":24,"storyPoints":5,"sprintIndex":3},
           {"title":"User acceptance testing","priority":"HIGH","type":"TASK","assignToRole":"MANAGER","estimateHours":16,"storyPoints":3,"sprintIndex":3,"milestoneIndex":2},
           {"title":"Prepare release and deploy","priority":"HIGH","type":"TASK","assignToRole":"MEMBER","estimateHours":8,"storyPoints":2,"sprintIndex":3,"milestoneIndex":2},
           {"title":"Retrospective and handover","priority":"LOW","type":"TASK","assignToRole":"MANAGER","estimateHours":4,"storyPoints":1,"sprintIndex":3}]}
        """;

    private static final String KANBAN = """
        {"project":{"name":"Support and Maintenance","priority":"MEDIUM"},
         "milestones":[{"name":"Board and process set up","dueOffsetDays":7},{"name":"First monthly review","dueOffsetDays":30}],
         "tasks":[
           {"title":"Set up board columns and workflow","priority":"MEDIUM","type":"TASK","assignToRole":"MANAGER","estimateHours":4,"dueOffsetDays":3,"milestoneIndex":0},
           {"title":"Triage and prioritise the backlog","priority":"HIGH","type":"TASK","assignToRole":"MANAGER","estimateHours":6,"dueOffsetDays":6,"milestoneIndex":0},
           {"title":"Agree response-time targets (SLAs)","priority":"MEDIUM","type":"TASK","assignToRole":"MANAGER","estimateHours":3,"dueOffsetDays":7,"milestoneIndex":0},
           {"title":"Handle priority tickets","priority":"HIGH","type":"TASK","assignToRole":"MEMBER","estimateHours":20,"dueOffsetDays":14},
           {"title":"Fix recurring issues","priority":"MEDIUM","type":"BUG","assignToRole":"MEMBER","estimateHours":16,"dueOffsetDays":21},
           {"title":"Document common fixes","priority":"LOW","type":"TASK","assignToRole":"MEMBER","estimateHours":8,"dueOffsetDays":25},
           {"title":"Monthly review and report","priority":"MEDIUM","type":"TASK","assignToRole":"MANAGER","estimateHours":4,"dueOffsetDays":30,"milestoneIndex":1}]}
        """;

    private static final String WATERFALL = """
        {"project":{"name":"Delivery Project","priority":"MEDIUM"},
         "phases":[
           {"name":"Requirements","startOffsetDays":0,"durationDays":14},
           {"name":"Design","startOffsetDays":14,"durationDays":21},
           {"name":"Build","startOffsetDays":35,"durationDays":56},
           {"name":"Testing","startOffsetDays":91,"durationDays":21},
           {"name":"Deployment","startOffsetDays":112,"durationDays":7}],
         "milestones":[
           {"name":"Requirements signed off","dueOffsetDays":13},{"name":"Design approved","dueOffsetDays":34},
           {"name":"Build complete","dueOffsetDays":90},{"name":"Testing complete","dueOffsetDays":111},{"name":"Go-live","dueOffsetDays":118}],
         "tasks":[
           {"title":"Gather and document requirements","priority":"HIGH","type":"TASK","assignToRole":"MANAGER","estimateHours":24,"dueOffsetDays":10,"milestoneIndex":0},
           {"title":"Get requirements sign-off","priority":"HIGH","type":"TASK","assignToRole":"MANAGER","estimateHours":4,"dueOffsetDays":13,"milestoneIndex":0},
           {"title":"Create system and screen design","priority":"HIGH","type":"TASK","assignToRole":"MEMBER","estimateHours":40,"dueOffsetDays":30,"milestoneIndex":1},
           {"title":"Review and approve design","priority":"HIGH","type":"TASK","assignToRole":"MANAGER","estimateHours":6,"dueOffsetDays":34,"milestoneIndex":1},
           {"title":"Develop modules","priority":"HIGH","type":"FEATURE","assignToRole":"MEMBER","estimateHours":160,"dueOffsetDays":75},
           {"title":"Integrate modules","priority":"MEDIUM","type":"TASK","assignToRole":"MEMBER","estimateHours":40,"dueOffsetDays":90,"milestoneIndex":2},
           {"title":"Write test plan and test cases","priority":"MEDIUM","type":"TASK","assignToRole":"MEMBER","estimateHours":24,"dueOffsetDays":98},
           {"title":"Run system and acceptance tests","priority":"HIGH","type":"TASK","assignToRole":"MEMBER","estimateHours":60,"dueOffsetDays":108},
           {"title":"Fix defects","priority":"HIGH","type":"BUG","assignToRole":"MEMBER","estimateHours":40,"dueOffsetDays":111,"milestoneIndex":3},
           {"title":"Deploy to production","priority":"CRITICAL","type":"TASK","assignToRole":"MEMBER","estimateHours":12,"dueOffsetDays":117,"milestoneIndex":4},
           {"title":"Hand over and close the project","priority":"MEDIUM","type":"TASK","assignToRole":"MANAGER","estimateHours":8,"dueOffsetDays":118,"milestoneIndex":4}]}
        """;

    private static final String MARKETING = """
        {"project":{"name":"Marketing Campaign","priority":"MEDIUM"},
         "phases":[
           {"name":"Plan","startOffsetDays":0,"durationDays":7},{"name":"Create","startOffsetDays":7,"durationDays":14},
           {"name":"Launch","startOffsetDays":21,"durationDays":7},{"name":"Review","startOffsetDays":28,"durationDays":7}],
         "milestones":[
           {"name":"Plan approved","dueOffsetDays":6},{"name":"Content ready","dueOffsetDays":20},
           {"name":"Campaign live","dueOffsetDays":21},{"name":"Results report delivered","dueOffsetDays":34}],
         "tasks":[
           {"title":"Set goals, audience and budget","priority":"HIGH","type":"TASK","assignToRole":"MANAGER","estimateHours":6,"dueOffsetDays":3,"milestoneIndex":0},
           {"title":"Plan channels and content calendar","priority":"MEDIUM","type":"TASK","assignToRole":"MEMBER","estimateHours":8,"dueOffsetDays":6,"milestoneIndex":0},
           {"title":"Write copy and messaging","priority":"MEDIUM","type":"TASK","assignToRole":"MEMBER","estimateHours":16,"dueOffsetDays":14},
           {"title":"Design creatives and landing page","priority":"MEDIUM","type":"TASK","assignToRole":"MEMBER","estimateHours":24,"dueOffsetDays":19},
           {"title":"Review and approve content","priority":"HIGH","type":"TASK","assignToRole":"MANAGER","estimateHours":4,"dueOffsetDays":20,"milestoneIndex":1},
           {"title":"Launch the campaign","priority":"HIGH","type":"TASK","assignToRole":"MEMBER","estimateHours":4,"dueOffsetDays":21,"milestoneIndex":2},
           {"title":"Monitor and optimise performance","priority":"MEDIUM","type":"TASK","assignToRole":"MEMBER","estimateHours":16,"dueOffsetDays":28},
           {"title":"Compile results report","priority":"MEDIUM","type":"TASK","assignToRole":"MANAGER","estimateHours":8,"dueOffsetDays":34,"milestoneIndex":3}]}
        """;

    private static final String OPERATIONS = """
        {"project":{"name":"Operations Rollout","priority":"MEDIUM"},
         "phases":[
           {"name":"Prepare","startOffsetDays":0,"durationDays":10},{"name":"Pilot","startOffsetDays":10,"durationDays":14},
           {"name":"Rollout","startOffsetDays":24,"durationDays":14},{"name":"Review","startOffsetDays":38,"durationDays":7}],
         "milestones":[
           {"name":"Plan approved","dueOffsetDays":9},{"name":"Pilot complete","dueOffsetDays":23},
           {"name":"Rollout complete","dueOffsetDays":37},{"name":"Review closed","dueOffsetDays":44}],
         "tasks":[
           {"title":"Document the current process and goals","priority":"MEDIUM","type":"TASK","assignToRole":"MANAGER","estimateHours":8,"dueOffsetDays":5,"milestoneIndex":0},
           {"title":"Prepare materials and training","priority":"MEDIUM","type":"TASK","assignToRole":"MEMBER","estimateHours":16,"dueOffsetDays":9,"milestoneIndex":0},
           {"title":"Run a pilot with one team","priority":"HIGH","type":"TASK","assignToRole":"MEMBER","estimateHours":24,"dueOffsetDays":20},
           {"title":"Collect feedback and adjust","priority":"MEDIUM","type":"TASK","assignToRole":"MEMBER","estimateHours":8,"dueOffsetDays":23,"milestoneIndex":1},
           {"title":"Roll out to all teams","priority":"HIGH","type":"TASK","assignToRole":"MEMBER","estimateHours":40,"dueOffsetDays":36},
           {"title":"Confirm everyone is trained","priority":"MEDIUM","type":"TASK","assignToRole":"MEMBER","estimateHours":12,"dueOffsetDays":37,"milestoneIndex":2},
           {"title":"Review results and close","priority":"MEDIUM","type":"TASK","assignToRole":"MANAGER","estimateHours":6,"dueOffsetDays":44,"milestoneIndex":3}]}
        """;
}