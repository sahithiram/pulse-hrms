package com.mentis.hrms.repository;

import com.mentis.hrms.model.DataMigrationIssue;
import com.mentis.hrms.model.enums.MigrationIssueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface DataMigrationIssueRepository extends JpaRepository<DataMigrationIssue, Long> {
    Optional<DataMigrationIssue> findByChecksum(String checksum);
    boolean existsByChecksum(String checksum);
    List<DataMigrationIssue> findByStatusOrderByCreatedAtDesc(MigrationIssueStatus status);
    List<DataMigrationIssue> findByEmployeeRefAndStatusOrderByCreatedAtDesc(String employeeRef, MigrationIssueStatus status);
    long countByStatus(MigrationIssueStatus status);
}
