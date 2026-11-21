package com.foodplant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import com.foodplant.entity.Models.QualityResult;

@Entity @Table(name="quality_checks", indexes=@Index(name="idx_quality_batch", columnList="batch_id")) @Getter @Setter @NoArgsConstructor
public class QualityCheck {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="batch_id") private Batch batch;
    @Column(name="checked_at", nullable=false) private Instant checkedAt;
    @Column(name="moisture_percent", precision=6, scale=2) private BigDecimal moisturePercent;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=12) private QualityResult result;
    @Column(length=1000) private String notes;
}
