package com.foodplant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import com.foodplant.entity.Models.*;

/** REST boundary objects; JPA entities are never returned to clients. */
public final class ApiDtos {
  private ApiDtos() {}
  public enum WeightUnit { KG, G }
  public record WeightInput(@NotNull @DecimalMin(value="0.0",inclusive=false) @Digits(integer=14,fraction=6) BigDecimal value,
                            @NotNull WeightUnit unit) {}
  public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {}
  public record TokenResponse(String accessToken, String tokenType, long expiresInSeconds, String role) {}
  public record UserRequest(@NotBlank @Email String email,
                            @NotBlank @Size(min=12,max=100) String password, @NotNull Role role) {}
  public record UserResponse(UUID id,String email,Role role,boolean enabled) {}
  public record SupplierRequest(@NotBlank @Size(max=150) String name,
                                @NotBlank @Email @Size(max=160) String contactEmail,
                                @NotBlank @Size(max=120) String originRegion, @NotNull Boolean active) {}
  public record SupplierResponse(UUID id,String name,String contactEmail,String originRegion,boolean active) {}
  public record ProductionLineRequest(@NotBlank @Pattern(regexp="[A-Z0-9-]{2,20}") String code,
                         @NotBlank @Size(max=140) String name,@NotNull @DecimalMin("0.001") @Digits(integer=11,fraction=3) BigDecimal capacityKgPerDay, @NotNull Boolean active) {}
  public record ProductionLineResponse(UUID id,String code,String name,BigDecimal capacityKgPerDay,boolean active) implements Serializable {}
  public record MaterialRequest(@NotBlank @Size(max=100) String lotCode,@NotNull UUID supplierId,
                                @NotBlank @Size(max=140) String materialName,@NotNull @Valid WeightInput openingStock,
                                LocalDate expiryDate) {}
  public record MaterialUpdateRequest(@NotBlank @Size(max=100) String lotCode,@NotNull UUID supplierId,
                                      @NotBlank @Size(max=140) String materialName,LocalDate expiryDate) {}
  public record MaterialResponse(UUID id,String lotCode,UUID supplierId,String materialName,
                                 BigDecimal stockKg,LocalDate expiryDate,boolean archived) {}
  public record StockAdjustmentRequest(@NotNull @Valid WeightInput change,
                                       @NotBlank @Size(max=180) String reason, @NotNull Boolean increase) {}
  public record MovementResponse(UUID id,UUID materialLotId,BigDecimal changeKg,String reason,Instant createdAt) {}
  public record BatchRequest(@NotBlank @Size(max=90) String batchNumber,@NotBlank @Size(max=150) String productName,
                             @NotNull UUID productionLineId,@NotNull UUID supplierId,
                             @NotNull @Valid WeightInput rawWeight,@NotNull @Valid WeightInput outputWeight,
                             @NotNull Instant producedAt,@NotNull Shift shift,
                             @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer=3,fraction=2) BigDecimal moisturePercent,
                             @NotNull BatchStatus status,@NotBlank @Size(max=80) String machineCode,@Size(max=1000) String notes) {}
  public record BatchResponse(UUID id,String batchNumber,String productName,UUID productionLineId,
                              UUID supplierId,BigDecimal rawWeightKg,BigDecimal outputWeightKg,
                              Instant producedAt,Shift shift,BigDecimal moisturePercent,
                              BatchStatus status,String machineCode,String notes) {}
  public record QualityRequest(@NotNull Instant checkedAt,
                               @DecimalMin("0.0") @DecimalMax("100.0") @Digits(integer=3,fraction=2) BigDecimal moisturePercent,
                               @NotNull QualityResult result,@Size(max=1000) String notes) {}
  public record QualityResponse(UUID id,UUID batchId,Instant checkedAt,
                                BigDecimal moisturePercent,QualityResult result,String notes) {}
  public record UsageRequest(@NotNull UUID materialLotId,@NotNull @Valid WeightInput quantity) {}
  public record UsageResponse(UUID id,UUID batchId,UUID materialLotId,BigDecimal quantityKg) {}
  public record TraceResponse(BatchResponse batch,SupplierResponse supplier,
                              ProductionLineResponse productionLine,
                              List<QualityResponse> qualityChecks,List<UsageResponse> materials) {}
}
