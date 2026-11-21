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

@RestController @RequestMapping("/api/v1/production-lines") @SecurityRequirement(name="bearerAuth")
public class ProductionLineController {
  private final PlantService service;
  public ProductionLineController(PlantService service) { this.service=service; }
  @GetMapping public Page<ProductionLineResponse> list(@RequestParam(required=false) String search,
      @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) { return service.listLines(search,page,size); }
  @GetMapping("/summary") public List<ProductionLineResponse> summary() { return service.lineSummary(); }
  @GetMapping("/{id}") public ProductionLineResponse get(@PathVariable UUID id) { return service.getLine(id); }
  @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @PostMapping public ResponseEntity<ProductionLineResponse> create(@Valid @RequestBody ProductionLineRequest request) {
    return ResponseEntity.status(201).body(service.createLine(request));
  }
  @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @PutMapping("/{id}") public ProductionLineResponse update(@PathVariable UUID id,@Valid @RequestBody ProductionLineRequest request) {
    return service.updateLine(id,request);
  }
  @PreAuthorize("hasRole('ADMIN')") @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.deleteLine(id);return ResponseEntity.noContent().build();
  }
}
