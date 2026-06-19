package com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackerweightservice.model.WaterLog;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaterLogRepository extends JpaRepository<WaterLog, Long> {
    @Query("select w from WaterLog w where w.userId = :userId and w.requestId = :requestId "
            + "and w.deleted = false")
    Optional<WaterLog> findByUserIdAndRequestId(
            @Param("userId") Long userId,
            @Param("requestId") String requestId
    );

    @Query("select w from WaterLog w where w.userId = :userId and w.requestId = :requestId")
    Optional<WaterLog> findAnyByUserIdAndRequestId(
            @Param("userId") Long userId,
            @Param("requestId") String requestId
    );

    @Query("select w from WaterLog w where w.id = :id and w.userId = :userId")
    Optional<WaterLog> findAnyByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    @Query("""
            select w from WaterLog w
            where w.userId = :userId
              and w.recordDate = :recordDate
              and w.deleted = false
            order by w.createdAt desc
            """)
    List<WaterLog> findAllByUserIdAndRecordDateOrderByCreatedAtDesc(
            @Param("userId") Long userId,
            @Param("recordDate") LocalDate recordDate
    );

    @Query("""
            select w from WaterLog w
            where w.userId = :userId
              and w.recordDate between :startDate and :endDate
              and w.deleted = false
            order by w.recordDate asc, w.createdAt asc
            """)
    List<WaterLog> findAllByUserIdAndRecordDateBetweenOrderByRecordDateAscCreatedAtAsc(
            @Param("userId") Long userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    @Query("""
            select w from WaterLog w
            where w.userId = :userId
              and w.updatedAt > :updatedAt
            order by w.updatedAt asc, w.id asc
            """)
    List<WaterLog> findAllChangedAfter(
            @Param("userId") Long userId,
            @Param("updatedAt") Instant updatedAt,
            Pageable pageable
    );

    void deleteAllByUserId(Long userId);
}
