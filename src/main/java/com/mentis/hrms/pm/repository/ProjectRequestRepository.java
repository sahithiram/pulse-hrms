package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.ProjectRequest;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ProjectRequestRepository extends JpaRepository<ProjectRequest, Long> {
    List<ProjectRequest> findAllByOrderByIdDesc();
    List<ProjectRequest> findByCreatedByOrderByIdDesc(Long createdBy);

    /** STEP 2: row lock so a request can only be decided once. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ProjectRequest r where r.id = :id")
    Optional<ProjectRequest> findByIdForUpdate(@Param("id") Long id);
}