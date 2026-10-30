package com.mentis.hrms.event;                       // ADAPT

import java.time.LocalDate;

public record EmployeeResignedEvent(Long employeeId, LocalDate lastWorkingDay, Long actorId) {}