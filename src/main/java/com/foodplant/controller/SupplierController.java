package com.foodplant.controller;

import com.foodplant.dto.ApiDtos.*;
import com.foodplant.service.PlantService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/suppliers") @SecurityRequirement(name="bearerAuth")
public class SupplierController {
  private final PlantService service;
  public SupplierController(PlantService service) { this.service=service; }
  @GetMapping public Page<SupplierResponse> list(@RequestParam(required=false) String search,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.listSuppliers(search,page,size); }
  @GetMapping("/{id}") public SupplierResponse get(@PathVariable UUID id) { return service.getSupplier(id); }
  @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @PostMapping public ResponseEntity<SupplierResponse> create(@Valid @RequestBody SupplierRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(service.createSupplier(request));
  }
  @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @PutMapping("/{id}") public SupplierResponse update(@PathVariable UUID id,@Valid @RequestBody SupplierRequest request) {
    return service.updateSupplier(id,request);
  }
  @PreAuthorize("hasRole('ADMIN')") @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.deleteSupplier(id);return ResponseEntity.noContent().build();
  }
}
