package com.olehprukhnytskyi.macrotrackerweightservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "weight_log_changes",
        indexes = @Index(name = "idx_weight_log_changes_user_cursor",
                columnList = "user_id, id")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeightLogChange {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "weight_log_id", nullable = false)
    private Long weightLogId;

    @Column(precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(name = "record_date")
    private LocalDate recordDate;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(nullable = false)
    private boolean deleted;
}
