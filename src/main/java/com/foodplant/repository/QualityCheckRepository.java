package com.foodplant.repository;
import com.foodplant.entity.QualityCheck;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface QualityCheckRepository extends JpaRepository<QualityCheck,UUID> {
    List<QualityCheck> findByBatchIdOrderByCheckedAtAsc(UUID batchId);
    boolean existsByBatchId(UUID batchId);
}
