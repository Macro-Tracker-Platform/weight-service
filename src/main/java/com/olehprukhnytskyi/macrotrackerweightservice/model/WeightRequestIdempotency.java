package com.olehprukhnytskyi.macrotrackerweightservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "weight_request_idempotency",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "request_id"})
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WeightRequestIdempotency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "weight_log_id")
    private Long weightLogId;

    @Column(precision = 5, scale = 2)
    private BigDecimal weight;

    @Column(name = "record_date")
    private LocalDate recordDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
