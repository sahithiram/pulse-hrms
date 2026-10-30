package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.SavedFilter;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedFilterRepository extends JpaRepository<SavedFilter, Long> {
    List<SavedFilter> findByOwnerIdOrderByNameAsc(Long ownerId);
    Optional<SavedFilter> findByOwnerIdAndProjectIdAndName(Long ownerId, Long projectId, String name);
    java.util.List<SavedFilter> findByOwnerIdAndProjectId(Long ownerId, Long projectId);
}