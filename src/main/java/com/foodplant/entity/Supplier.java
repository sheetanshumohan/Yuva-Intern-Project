package com.foodplant.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import java.util.UUID;

@Entity @Table(name="suppliers") @Getter @Setter @NoArgsConstructor
public class Supplier {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private UUID id;
    @Column(nullable=false, unique=true, length=150) private String name;
    @Column(name="contact_email", nullable=false, length=160) private String contactEmail;
    @Column(name="origin_region", nullable=false, length=120) private String originRegion;
    @Column(nullable=false) private boolean active = true;
}
