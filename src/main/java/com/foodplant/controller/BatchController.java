package com.foodplant.controller;

import com.foodplant.dto.ApiDtos.*;
import com.foodplant.entity.Models.*;
import com.foodplant.service.PlantService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/batches") @SecurityRequirement(name="bearerAuth")
public class BatchController {
  private final PlantService service;
  public BatchController(PlantService service) { this.service=service; }
  @GetMapping public Page<BatchResponse> list(@RequestParam(required=false) String search,
      @RequestParam(required=false) UUID lineId,@RequestParam(required=false) BatchStatus status,
      @RequestParam(required=false) Shift shift,@RequestParam(defaultValue="0") int page,
      @RequestParam(defaultValue="20") int size) { return service.listBatches(search,lineId,status,shift,page,size); }
  @GetMapping("/lookup") public List<BatchResponse> lookup(@RequestParam String batchNumber) { return service.byBatchNumber(batchNumber); }
  @GetMapping("/{id}") public BatchResponse get(@PathVariable UUID id) { return service.getBatch(id); }
  @GetMapping("/{id}/trace") public TraceResponse trace(@PathVariable UUID id) { return service.trace(id); }
  @GetMapping("/{id}/quality-checks") public List<QualityResponse> checks(@PathVariable UUID id) { return service.qualityChecks(id); }
  @GetMapping("/{id}/materials") public List<UsageResponse> materials(@PathVariable UUID id) { return service.batchMaterials(id); }
  @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @PostMapping public ResponseEntity<BatchResponse> create(@Valid @RequestBody BatchRequest request) {
    return ResponseEntity.status(201).body(service.createBatch(request));
  }
  @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @PutMapping("/{id}") public BatchResponse update(@PathVariable UUID id,@Valid @RequestBody BatchRequest request) {
    return service.updateBatch(id,request);
  }
  @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @PostMapping("/{id}/quality-checks") public ResponseEntity<QualityResponse> quality(
      @PathVariable UUID id,@Valid @RequestBody QualityRequest request) { return ResponseEntity.status(201).body(service.addQualityCheck(id,request)); }
  @PreAuthorize("hasAnyRole('ADMIN','STAFF')") @PostMapping("/{id}/materials") public ResponseEntity<UsageResponse> consume(
      @PathVariable UUID id,@Valid @RequestBody UsageRequest request) { return ResponseEntity.status(201).body(service.consumeMaterial(id,request)); }
  @PreAuthorize("hasRole('ADMIN')") @DeleteMapping("/{id}") public ResponseEntity<Void> delete(@PathVariable UUID id) {
    service.deleteBatch(id);return ResponseEntity.noContent().build();
  }
}
