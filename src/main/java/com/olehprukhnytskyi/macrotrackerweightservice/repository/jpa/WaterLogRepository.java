package com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackerweightservice.model.WaterLog;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaterLogRepository extends JpaRepository<WaterLog, Long> {
    Optional<WaterLog> findByUserIdAndRequestId(Long userId, String requestId);

    List<WaterLog> findAllByUserIdAndRecordDateOrderByCreatedAtDesc(
            Long userId,
            LocalDate recordDate
    );

    List<WaterLog> findAllByUserIdAndRecordDateBetweenOrderByRecordDateAscCreatedAtAsc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate
    );

    void deleteByIdAndUserId(Long id, Long userId);

    void deleteAllByUserId(Long userId);
}
