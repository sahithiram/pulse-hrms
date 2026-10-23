package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.Team;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    boolean existsByName(String name);

    List<Team> findByStatus(String status);
    List<Team> findByLeadEmployeeId(Long leadEmployeeId);
    @Query("SELECT t FROM Team t WHERE " +
            "(:search IS NULL OR LOWER(t.name) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<Team> search(@Param("search") String search, Pageable pageable);
}