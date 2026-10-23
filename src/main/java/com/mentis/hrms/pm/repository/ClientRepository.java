package com.mentis.hrms.pm.repository;

import com.mentis.hrms.pm.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClientRepository extends JpaRepository<Client, Long> {

    boolean existsByName(String name);

    List<Client> findByStatus(String status);

    @Query("SELECT c FROM Client c WHERE " +
            "(:search IS NULL OR LOWER(c.name) LIKE LOWER(CONCAT('%', :search, '%'))) " +
            "AND (:status IS NULL OR c.status = :status)")
    Page<Client> search(@Param("search") String search,
                        @Param("status") String status,
                        Pageable pageable);
}