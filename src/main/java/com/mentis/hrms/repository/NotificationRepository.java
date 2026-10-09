package com.mentis.hrms.repository;

import com.mentis.hrms.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime; // ADD THIS IMPORT
import java.util.List;
import java.util.Optional;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRecipientIdAndRecipientTypeOrderByCreatedAtDesc(String recipientId, String recipientType);

    // FIXED: Add correct method signature for findPersistentByRecipientIdAndRecipientTypeOrderByCreatedAtDesc
    @Query("SELECT n FROM Notification n WHERE n.recipientId = ?1 AND n.recipientType = ?2 AND n.persistent = true ORDER BY n.createdAt DESC")
    List<Notification> findPersistentByRecipientIdAndRecipientTypeOrderByCreatedAtDesc(String recipientId, String recipientType);

    // FIXED: Add correct method signature for countPersistentUnreadByRecipientIdAndRecipientType
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipientId = ?1 AND n.recipientType = ?2 AND n.readAt IS NULL AND n.persistent = true")
    long countPersistentUnreadByRecipientIdAndRecipientType(String recipientId, String recipientType);

    // Original method for backward compatibility
    @Query("SELECT COUNT(n) FROM Notification n WHERE n.recipientId = ?1 AND n.recipientType = ?2 AND n.readAt IS NULL")
    long countByRecipientIdAndRecipientTypeAndReadAtIsNull(String recipientId, String recipientType);

    // FIXED: Make sure this method returns Optional<Notification>
    Optional<Notification> findByIdAndRecipientIdAndRecipientType(Long id, String recipientId, String recipientType);

    @Modifying
    @Query("UPDATE Notification n SET n.readAt = CURRENT_TIMESTAMP WHERE n.recipientId = :recipientId AND n.recipientType = :recipientType AND n.readAt IS NULL")
    void markAllAsRead(@Param("recipientId") String recipientId, @Param("recipientType") String recipientType);

    // ADD THESE NEW METHODS FOR CLEANUP SERVICE:
    @Modifying
    @Query("DELETE FROM Notification n WHERE n.persistent = false AND n.createdAt < :cutoffDate")
    int deleteTemporaryNotificationsOlderThan(@Param("cutoffDate") LocalDateTime cutoffDate);
    // ─── PHASE 4 queries ─────────────────────────────────────────

    /** Filter inbox by action type (e.g. "TASK_MENTIONED"). */
    java.util.List<Notification> findByRecipientIdAndRecipientTypeAndActionOrderByCreatedAtDesc(
            String recipientId, String recipientType, String action);

    /** Recent N notifications for a user. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT n FROM Notification n " +
                    "WHERE n.recipientId = :rid AND n.recipientType = :rtype " +
                    "ORDER BY n.createdAt DESC")
    java.util.List<Notification> findRecent(
            @org.springframework.data.repository.query.Param("rid") String recipientId,
            @org.springframework.data.repository.query.Param("rtype") String recipientType,
            org.springframework.data.domain.Pageable pageable);

    /** All by task — for dedupe on due-soon and overdue. */
    java.util.List<Notification> findByTaskIdAndActionOrderByCreatedAtDesc(Long taskId, String action);
    @Modifying
    @Query("UPDATE Notification n SET n.readAt = CURRENT_TIMESTAMP WHERE n.persistent = false AND n.readAt IS NULL AND n.createdAt < :cutoffDate")
    void markOldTemporaryAsRead(@Param("cutoffDate") LocalDateTime cutoffDate);
}
