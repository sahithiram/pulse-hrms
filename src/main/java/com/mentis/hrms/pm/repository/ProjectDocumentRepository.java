package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.ProjectDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, Long> {
    List<ProjectDocument> findByProjectIdOrderByUploadedAtDesc(Long projectId);
}