package com.mentis.hrms.repository;

import com.mentis.hrms.model.AccessPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface AccessPermissionRepository extends JpaRepository<AccessPermission, Long> {
    List<AccessPermission> findByRole(String role);
    Optional<AccessPermission> findByRoleAndModuleAndAction(String role, String module, String action);
}
