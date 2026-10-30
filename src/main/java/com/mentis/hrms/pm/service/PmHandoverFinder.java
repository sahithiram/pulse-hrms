package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.repository.*;
import com.mentis.hrms.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class PmHandoverFinder {

    public record TaskRef(Long id, String title, Long projectId, String projectName,
                          String priority, LocalDate dueDate, String status) {}
    public record Ref(Long id, String name) {}
    public record Detail(Long employeeId, String name, LocalDate lastWorkingDay,
                         List<TaskRef> tasks, List<Ref> ownedProjects, List<Ref> ledTeams) {}

    private final EmployeeRepository employeeRepo;
    private final TaskRepository taskRepo;
    private final ProjectRepository projectRepo;
    private final TeamRepository teamRepo;

    @Transactional(readOnly = true)
    public Detail detail(Long employeeId) {
        var emp = employeeRepo.findById(employeeId).orElse(null);
        if (emp == null) return null;
        String name = ((emp.getFirstName() == null ? "" : emp.getFirstName()) + " "
                + (emp.getLastName() == null ? "" : emp.getLastName())).trim();

        List<TaskRef> tasks = new ArrayList<>();
        for (var t : taskRepo.findOpenByAssigneeId(employeeId)) {
            String projectName = t.getProjectId() == null
                    ? null
                    : projectRepo.findById(t.getProjectId())
                    .map(p -> p.getName())
                    .orElse("Project #" + t.getProjectId());
            tasks.add(new TaskRef(
                    t.getId(),
                    t.getTitle(),
                    t.getProjectId(),
                    projectName,
                    t.getPriority() == null ? null : t.getPriority().name(),
                    t.getDueDate(),
                    t.getStatus() == null ? null : t.getStatus().name()));
        }

        List<Ref> projects = new ArrayList<>();
        for (var p : projectRepo.findActiveByOwnerId(employeeId))
            projects.add(new Ref(p.getId(), p.getName()));

        List<Ref> teams = new ArrayList<>();
        for (var t : teamRepo.findByLeadEmployeeId(employeeId))
            teams.add(new Ref(t.getId(), t.getName()));

        return new Detail(
                employeeId,
                name.isEmpty() ? "Employee #" + employeeId : name,
                null,   // Employee has no lastWorkingDay field; wire your own source here later
                tasks, projects, teams);
    }
}