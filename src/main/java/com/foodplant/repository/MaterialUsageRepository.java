package com.foodplant.repository;
import com.foodplant.entity.MaterialUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface MaterialUsageRepository extends JpaRepository<MaterialUsage,UUID> {
    List<MaterialUsage> findByBatchId(UUID batchId);
    boolean existsByBatchId(UUID batchId);
    boolean existsByMaterialLotId(UUID materialLotId);
}
