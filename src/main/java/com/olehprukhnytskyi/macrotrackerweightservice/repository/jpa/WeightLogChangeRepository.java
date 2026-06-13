package com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackerweightservice.model.WeightLogChange;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeightLogChangeRepository extends JpaRepository<WeightLogChange, Long> {
    List<WeightLogChange> findAllByUserIdAndIdGreaterThanOrderById(
            Long userId,
            Long id,
            Pageable pageable
    );

    void deleteAllByUserId(Long userId);
}
