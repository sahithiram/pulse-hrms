package com.mentis.hrms.pm.security;

import com.mentis.hrms.model.enums.UserRole;
import jakarta.servlet.http.HttpServletRequest;

public interface PmUserProvider {

    PmUser resolve(HttpServletRequest request);

    record PmUser(
            Long employeePk,
            String employeeId,
            String fullName,
            String email,
            UserRole hrmsRole
    ) {}

    class NotAuthenticatedException extends RuntimeException {
        public NotAuthenticatedException(String message) { super(message); }
    }
}