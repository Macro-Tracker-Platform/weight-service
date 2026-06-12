package com.olehprukhnytskyi.macrotrackerweightservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "water_logs",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "request_id"}),
        indexes = @Index(name = "idx_water_logs_user_date",
                columnList = "user_id, record_date, created_at DESC")
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaterLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "amount_ml", nullable = false)
    private int amountMl;

    @Column(name = "created_at", nullable = false)
    private long createdAt;

    @Column(name = "record_date", nullable = false)
    private LocalDate recordDate;
}
