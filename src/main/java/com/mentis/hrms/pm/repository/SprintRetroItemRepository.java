package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.SprintRetroItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface SprintRetroItemRepository extends JpaRepository<SprintRetroItem, Long> {
    List<SprintRetroItem> findByRetroIdOrderBySortOrderAscIdAsc(Long retroId);
    List<SprintRetroItem> findByRetroIdIn(Collection<Long> retroIds);
}