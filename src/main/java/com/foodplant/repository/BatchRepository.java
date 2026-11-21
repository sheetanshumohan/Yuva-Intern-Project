package com.foodplant.repository;
import com.foodplant.entity.Batch;
import org.springframework.data.jpa.repository.*;
import java.util.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
public interface BatchRepository extends JpaRepository<Batch,UUID>, JpaSpecificationExecutor<Batch> {
    List<Batch> findByBatchNumberOrderByProducedAtAsc(String batchNumber);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select b from Batch b where b.id=:id")
    Optional<Batch> findByIdForUpdate(@Param("id") UUID id);
}
