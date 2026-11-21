package com.foodplant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.UUID;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity @Table(name="raw_material_lots") @Getter @Setter @NoArgsConstructor
public class RawMaterialLot {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(name="lot_code", nullable=false, unique=true, length=100) private String lotCode;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="supplier_id") private Supplier supplier;
    @Column(name="material_name", nullable=false, length=140) private String materialName;
    @Column(name="stock_kg", nullable=false, precision=14, scale=3) private BigDecimal stockKg;
    @Column(name="expiry_date") private LocalDate expiryDate;
    @Column(nullable=false) private boolean archived = false;
    @Version private long version;
}
