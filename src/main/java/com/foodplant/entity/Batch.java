package com.foodplant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;
import com.foodplant.entity.Models.*;

@Entity @Table(name="batches", indexes={@Index(name="idx_batch_number",columnList="batch_number"), @Index(name="idx_batches_produced",columnList="produced_at")})
@Getter @Setter @NoArgsConstructor
public class Batch {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    // batchNumber is deliberately NOT unique: legacy identifiers were reused after a reset.
    @Column(name="batch_number", nullable=false, length=90) private String batchNumber;
    @Column(name="product_name", nullable=false, length=150) private String productName;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="production_line_id") private ProductionLine productionLine;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="supplier_id") private Supplier supplier;
    @Column(name="raw_weight_kg", nullable=false, precision=14, scale=3) private BigDecimal rawWeightKg;
    @Column(name="output_weight_kg", nullable=false, precision=14, scale=3) private BigDecimal outputWeightKg;
    @Column(name="produced_at", nullable=false) private Instant producedAt;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=12) private Shift shift;
    @Column(name="moisture_percent", precision=6, scale=2) private BigDecimal moisturePercent;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=20) private BatchStatus status;
    @Column(name="machine_code", nullable=false, length=80) private String machineCode;
    @Column(name="notes", length=1000) private String notes;
    @Version private long version;
}
