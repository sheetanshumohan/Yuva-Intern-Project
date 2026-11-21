package com.foodplant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.UUID;
import java.math.BigDecimal;

@Entity @Table(name="material_usages") @Getter @Setter @NoArgsConstructor
public class MaterialUsage {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="batch_id") private Batch batch;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="material_lot_id") private RawMaterialLot materialLot;
    @Column(name="quantity_kg",nullable=false,precision=14,scale=3) private BigDecimal quantityKg;
}
