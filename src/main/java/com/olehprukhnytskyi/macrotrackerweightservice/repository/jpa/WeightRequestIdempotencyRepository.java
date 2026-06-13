package com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackerweightservice.model.WeightRequestIdempotency;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeightRequestIdempotencyRepository
        extends JpaRepository<WeightRequestIdempotency, Long> {
    @Modifying
    @Query(value = """
            INSERT INTO weight_request_idempotency (user_id, request_id, created_at)
            VALUES (:userId, :requestId, :createdAt)
            ON CONFLICT (user_id, request_id) DO NOTHING
            """, nativeQuery = true)
    void reserve(
            @Param("userId") Long userId,
            @Param("requestId") String requestId,
            @Param("createdAt") Instant createdAt
    );

    @Modifying
    @Query(value = """
            MERGE INTO weight_request_idempotency (user_id, request_id, created_at)
            KEY (user_id, request_id)
            VALUES (:userId, :requestId, :createdAt)
            """, nativeQuery = true)
    void reserveForH2(
            @Param("userId") Long userId,
            @Param("requestId") String requestId,
            @Param("createdAt") Instant createdAt
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<WeightRequestIdempotency> findByUserIdAndRequestId(Long userId, String requestId);

    void deleteAllByUserId(Long userId);
}
