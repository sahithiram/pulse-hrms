package com.mentis.hrms.pm.security;

public final class PmCurrentUserHolder {

    private static final ThreadLocal<PmUserProvider.PmUser> CURRENT = new ThreadLocal<>();

    private PmCurrentUserHolder() {}

    public static void set(PmUserProvider.PmUser user) { CURRENT.set(user); }
    public static PmUserProvider.PmUser get() { return CURRENT.get(); }
    public static void clear() { CURRENT.remove(); }
}