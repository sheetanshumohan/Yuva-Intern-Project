package com.foodplant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name="inventory_movements") @Getter @Setter @NoArgsConstructor
public class InventoryMovement {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @ManyToOne(fetch=FetchType.LAZY,optional=false) @JoinColumn(name="material_lot_id") private RawMaterialLot materialLot;
    @Column(name="change_kg",nullable=false,precision=14,scale=3) private BigDecimal changeKg;
    @Column(nullable=false,length=180) private String reason;
    @Column(name="created_at",nullable=false) private Instant createdAt;
}
