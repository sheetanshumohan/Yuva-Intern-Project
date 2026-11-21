package com.foodplant.repository;
import com.foodplant.entity.ProductionLine;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface ProductionLineRepository extends org.springframework.data.jpa.repository.JpaSpecificationExecutor<com.foodplant.entity.ProductionLine>, JpaRepository<ProductionLine,UUID> { Optional<ProductionLine> findByCode(String code); }
