package com.foodplant.repository;
import com.foodplant.entity.InventoryMovement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface InventoryMovementRepository extends JpaRepository<InventoryMovement,UUID> {
    List<InventoryMovement> findByMaterialLotIdOrderByCreatedAtDesc(UUID materialLotId);
}
