package com.foodplant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.UUID;
import java.math.BigDecimal;

@Entity @Table(name="production_lines") @Getter @Setter @NoArgsConstructor
public class ProductionLine {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false, unique=true, length=20) private String code;
    @Column(nullable=false, length=140) private String name;
    @Column(name="capacity_kg_per_day", nullable=false, precision=14, scale=3) private BigDecimal capacityKgPerDay;
    @Column(nullable=false) private boolean active = true;
}
