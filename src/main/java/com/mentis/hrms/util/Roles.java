package com.mentis.hrms.util;

/** Canonical role comparison (string session values compared once, case-safe). */
public final class Roles {
    private Roles() {}
    public static boolean is(String role, String canonicalUpper) {
        return role != null && role.trim().toUpperCase().equals(canonicalUpper);
    }
    public static boolean isHr(String role) {
        return is(role, "HR") || is(role, "HR_MANAGER");
    }
    public static boolean isSuperAdmin(String role) { return is(role, "SUPER_ADMIN"); }
    public static boolean isAnyHrOrSuper(String role) { return isSuperAdmin(role) || isHr(role); }
}
