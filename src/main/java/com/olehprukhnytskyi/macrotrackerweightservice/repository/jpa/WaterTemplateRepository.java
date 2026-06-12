package com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackerweightservice.model.WaterTemplate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WaterTemplateRepository extends JpaRepository<WaterTemplate, Long> {
    List<WaterTemplate> findAllByUserIdOrderByAmountMl(Long userId);

    Optional<WaterTemplate> findByUserIdAndAmountMl(Long userId, int amountMl);

    void deleteAllByUserId(Long userId);
}
