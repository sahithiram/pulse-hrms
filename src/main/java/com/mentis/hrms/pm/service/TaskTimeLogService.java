package com.mentis.hrms.pm.service;

import com.mentis.hrms.pm.dto.request.LogTimeRequest;
import com.mentis.hrms.pm.dto.request.UpdateTimeLogRequest;
import com.mentis.hrms.pm.dto.response.TimeLogResponse;

import java.util.List;

public interface TaskTimeLogService {
    TimeLogResponse log(Long taskId, LogTimeRequest req, Long userId);
    List<TimeLogResponse> list(Long taskId);
    TimeLogResponse update(Long id, UpdateTimeLogRequest req, Long userId);
    void delete(Long id, Long userId);
}