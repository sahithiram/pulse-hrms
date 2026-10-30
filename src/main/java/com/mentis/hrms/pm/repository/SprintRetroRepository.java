package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.SprintRetro;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SprintRetroRepository extends JpaRepository<SprintRetro, Long> {
    Optional<SprintRetro> findBySprintId(Long sprintId);
    List<SprintRetro> findBySprintIdIn(Collection<Long> sprintIds);
}