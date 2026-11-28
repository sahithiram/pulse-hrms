package com.mentis.hrms.repository;

import com.mentis.hrms.model.Requisition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface RequisitionRepository extends JpaRepository<Requisition, Long> {
    List<Requisition> findByStatusOrderByCreatedAtDesc(String status);
    List<Requisition> findTop50ByOrderByCreatedAtDesc();

    @Query("SELECT COALESCE(MAX(r.id), 0) FROM Requisition r")
    Long maxId();
}
