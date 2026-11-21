package com.foodplant.repository;
import com.foodplant.entity.RawMaterialLot;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;
public interface RawMaterialLotRepository extends org.springframework.data.jpa.repository.JpaSpecificationExecutor<com.foodplant.entity.RawMaterialLot>, JpaRepository<RawMaterialLot,UUID> {
    Optional<RawMaterialLot> findByLotCode(String lotCode);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select r from RawMaterialLot r where r.id=:id")
    Optional<RawMaterialLot> findByIdForUpdate(@Param("id") UUID id);
}
