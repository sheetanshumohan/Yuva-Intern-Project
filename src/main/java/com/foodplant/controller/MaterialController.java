package com.foodplant.controller;

import com.foodplant.dto.ApiDtos.*;
import com.foodplant.service.PlantService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/raw-materials") @SecurityRequirement(name="bearerAuth")
public class MaterialController {
  private final PlantService service;
  public MaterialController(PlantService service) { this.service=service; }
  @GetMapping public Page<MaterialResponse> list(@RequestParam(required=false) String search,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.listMaterials(search,page,size); }
  @GetMapping("/{id}") public MaterialResponse get(@PathVariable UUID id) { return service.getMaterial(id); }
  @GetMapping("/{id}/movements") public List<MovementResponse> movements(@PathVariable UUID id) { return service.movements(id); }
  @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @PostMapping public ResponseEntity<MaterialResponse> create(@Valid @RequestBody MaterialRequest request) {
    return ResponseEntity.status(201).body(service.createMaterial(request));
  }
  @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @PutMapping("/{id}") public MaterialResponse update(@PathVariable UUID id,@Valid @RequestBody MaterialUpdateRequest request) {
    return service.updateMaterial(id,request);
  }
  @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @PostMapping("/{id}/adjustments") public MaterialResponse adjust(
      @PathVariable UUID id,@Valid @RequestBody StockAdjustmentRequest request) { return service.adjustStock(id,request); }
  @PreAuthorize("hasRole('ADMIN')") @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.deleteMaterial(id);return ResponseEntity.noContent().build();
  }
}
