package com.olehprukhnytskyi.macrotrackerweightservice.repository.jpa;

import com.olehprukhnytskyi.macrotrackerweightservice.model.WaterTemplate;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WaterTemplateRepository extends JpaRepository<WaterTemplate, Long> {
    @Query("select t from WaterTemplate t where t.userId = :userId and t.deleted = false "
            + "order by t.amountMl")
    List<WaterTemplate> findAllByUserIdOrderByAmountMl(@Param("userId") Long userId);

    @Query("select t from WaterTemplate t where t.userId = :userId and t.amountMl = :amountMl "
            + "and t.deleted = false")
    Optional<WaterTemplate> findByUserIdAndAmountMl(
            @Param("userId") Long userId,
            @Param("amountMl") int amountMl
    );

    @Query("select t from WaterTemplate t where t.id = :id and t.userId = :userId")
    Optional<WaterTemplate> findAnyByIdAndUserId(
            @Param("id") Long id,
            @Param("userId") Long userId
    );

    @Query("select t from WaterTemplate t where t.userId = :userId and t.amountMl = :amountMl")
    Optional<WaterTemplate> findAnyByUserIdAndAmountMl(
            @Param("userId") Long userId,
            @Param("amountMl") int amountMl
    );

    @Query("""
            select t from WaterTemplate t
            where t.userId = :userId
              and t.updatedAt > :updatedAt
            order by t.updatedAt asc, t.id asc
            """)
    List<WaterTemplate> findAllChangedAfter(
            @Param("userId") Long userId,
            @Param("updatedAt") Instant updatedAt,
            Pageable pageable
    );

    void deleteAllByUserId(Long userId);
}
