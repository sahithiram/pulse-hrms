package com.mentis.hrms.service;

import com.mentis.hrms.model.AccessPermission;
import com.mentis.hrms.repository.AccessPermissionRepository;
import com.mentis.hrms.util.Roles;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Cached view of access_permission. Deny-by-default for known modules;\n * SUPER_ADMIN short-circuits. Refreshes every 60s and on demand (after admin edits).\n */
@Service
public class PermissionMatrix {

    public static final String ACT_MANAGE = "MANAGE";
    public static final String ACT_VIEW   = "VIEW";
    public static final String ACT_CREATE = "CREATE";
    public static final String ACT_EDIT   = "EDIT";
    public static final String ACT_DELETE = "DELETE";
    public static final String ACT_APPROVE= "APPROVE";
    public static final String ACT_EXPORT = "EXPORT";

    private static final Logger logger = LoggerFactory.getLogger(PermissionMatrix.class);

    private final AccessPermissionRepository repo;
    private final Map<String, Map<String, Boolean>> cache = new ConcurrentHashMap<>();

    @Autowired
    public PermissionMatrix(AccessPermissionRepository repo) {
        this.repo = repo;
    }

    @PostConstruct
    public void load() { refresh(); }

    @Scheduled(fixedDelayString = "${hrms.security.permission-refresh-ms:60000}")
    public synchronized void refresh() {
        try {
            Map<String, Map<String, Boolean>> fresh = new HashMap<>();
            for (AccessPermission p : repo.findAll()) {
                fresh.computeIfAbsent(p.getRole().toUpperCase(Locale.ROOT), k -> new HashMap<>())
                     .put(p.getModule().toLowerCase(Locale.ROOT) + ":" + p.getAction().toUpperCase(Locale.ROOT),
                          p.isAllowed());
            }
            cache.clear();
            cache.putAll(fresh);
            logger.debug("Permission matrix refreshed: {} roles", fresh.size());
        } catch (Exception e) {
            logger.error("Failed to refresh permission matrix: {}", e.toString());
        }
    }

    public boolean isAllowed(String role, String module, String action) {
        if (role == null) return false;
        if (Roles.isSuperAdmin(role)) return true;
        String r = role.trim().toUpperCase(Locale.ROOT);
        String key = module.toLowerCase(Locale.ROOT) + ":" + action.toUpperCase(Locale.ROOT);
        Map<String, Boolean> matrix = cache.get(r);
        if (matrix != null && matrix.containsKey(key)) return matrix.get(key);
        // MANAGE implies every other action in the module
        Map<String, Boolean> m = cache.get(r);
        if (m != null && Boolean.TRUE.equals(m.get(module.toLowerCase(Locale.ROOT) + ":" + ACT_MANAGE))) return true;
        return false; // deny by default
    }

    public List<String> describe(String role) {
        if (Roles.isSuperAdmin(role)) return List.of("*:*");
        Map<String, Boolean> m = cache.get(role.trim().toUpperCase(Locale.ROOT));
        if (m == null) return List.of();
        return m.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).sorted().collect(Collectors.toList());
    }
}
