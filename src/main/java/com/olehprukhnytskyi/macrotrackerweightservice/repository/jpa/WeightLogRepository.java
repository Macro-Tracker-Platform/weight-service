package com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackerweightservice.model.WeightLog;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WeightLogRepository extends JpaRepository<WeightLog, Long> {
    Page<WeightLog> findAllByUserId(Long userId, Pageable pageable);

    @Modifying
    @Query(value = """
            INSERT INTO weight_logs (user_id, weight, record_date, source, created_at)
            VALUES (:userId, :weight, :recordDate, :source, NOW())
            ON CONFLICT (user_id, record_date)
            DO UPDATE SET 
                weight = EXCLUDED.weight, 
                source = EXCLUDED.source
            """, nativeQuery = true)
    void upsertWeight(
            @Param("userId") Long userId,
            @Param("weight") BigDecimal weight,
            @Param("recordDate") LocalDate recordDate,
            @Param("source") String source
    );

    Optional<WeightLog> findByUserIdAndRecordDate(Long userId, LocalDate recordDate);

    Optional<WeightLog> findByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndRecordDate(Long userId, LocalDate recordDate);

    void deleteByUserIdAndRecordDate(Long userId, LocalDate recordDate);

    @Modifying
    @Query(value = """
            DELETE FROM weight_logs 
            WHERE id IN (
                SELECT id FROM weight_logs 
                WHERE user_id = :userId 
                LIMIT :batchSize
            )
            """, nativeQuery = true)
    int deleteBatchByUserId(@Param("userId") Long userId, @Param("batchSize") int batchSize);
}
