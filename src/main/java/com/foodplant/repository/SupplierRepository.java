package com.foodplant.repository;
import com.foodplant.entity.Supplier;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SupplierRepository extends org.springframework.data.jpa.repository.JpaSpecificationExecutor<com.foodplant.entity.Supplier>, JpaRepository<Supplier,UUID> { boolean existsByNameIgnoreCase(String name); }
