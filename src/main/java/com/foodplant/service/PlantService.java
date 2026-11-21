package com.foodplant.service;

import com.foodplant.dto.ApiDtos.*;
import com.foodplant.entity.*;
import com.foodplant.entity.Models.*;
import com.foodplant.exception.ConflictException;
import com.foodplant.repository.*;
import org.springframework.cache.annotation.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.*;
import com.foodplant.util.WeightNormalizer;
import java.time.Instant;
import java.util.*;

/** Business layer owns weight normalization, stock integrity, lookup and trace assembly. */
@Service @Transactional
public class PlantService {
  private final SupplierRepository suppliers;
  private final ProductionLineRepository lines;
  private final RawMaterialLotRepository materials;
  private final BatchRepository batches;
  private final QualityCheckRepository checks;
  private final MaterialUsageRepository usages;
  private final InventoryMovementRepository movements;
  public PlantService(SupplierRepository suppliers,ProductionLineRepository lines,RawMaterialLotRepository materials,
                      BatchRepository batches,QualityCheckRepository checks,MaterialUsageRepository usages,
                      InventoryMovementRepository movements) {
    this.suppliers=suppliers;this.lines=lines;this.materials=materials;this.batches=batches;
    this.checks=checks;this.usages=usages;this.movements=movements;
  }
  public static BigDecimal kg(WeightInput input) {
    return WeightNormalizer.kilograms(input==null?null:input.value(),
        input==null||input.unit()==null?null:input.unit().name());
  }
  private static Pageable paging(int page,int size,String property) {
    if(page<0 || size<1 || size>100) throw new IllegalArgumentException("page must be >=0 and size must be between 1 and 100");
    return PageRequest.of(page,size,Sort.by(property).ascending());
  }
  private static <T> Specification<T> contains(String field,String search) {
    return (root,query,cb)-> search==null||search.isBlank()?cb.conjunction():
        cb.like(cb.lower(root.get(field).as(String.class)),"%"+search.toLowerCase(Locale.ROOT)+"%");
  }
  private Supplier supplier(UUID id) { return suppliers.findById(id).orElseThrow(()->new NoSuchElementException("Supplier not found")); }
  private ProductionLine line(UUID id) { return lines.findById(id).orElseThrow(()->new NoSuchElementException("Production line not found")); }
  private RawMaterialLot material(UUID id) { return materials.findById(id).orElseThrow(()->new NoSuchElementException("Raw material lot not found")); }
  private Batch batch(UUID id) { return batches.findById(id).orElseThrow(()->new NoSuchElementException("Batch not found")); }

  private static SupplierResponse asSupplier(Supplier s) { return new SupplierResponse(s.getId(),s.getName(),s.getContactEmail(),s.getOriginRegion(),s.isActive()); }
  private static ProductionLineResponse asLine(ProductionLine l) { return new ProductionLineResponse(l.getId(),l.getCode(),l.getName(),l.getCapacityKgPerDay(),l.isActive()); }
  private static MaterialResponse asMaterial(RawMaterialLot m) { return new MaterialResponse(m.getId(),m.getLotCode(),m.getSupplier().getId(),m.getMaterialName(),m.getStockKg(),m.getExpiryDate(),m.isArchived()); }
  private static BatchResponse asBatch(Batch b) { return new BatchResponse(b.getId(),b.getBatchNumber(),b.getProductName(),b.getProductionLine().getId(),b.getSupplier().getId(),b.getRawWeightKg(),b.getOutputWeightKg(),b.getProducedAt(),b.getShift(),b.getMoisturePercent(),b.getStatus(),b.getMachineCode(),b.getNotes()); }
  private static QualityResponse asQuality(QualityCheck q) { return new QualityResponse(q.getId(),q.getBatch().getId(),q.getCheckedAt(),q.getMoisturePercent(),q.getResult(),q.getNotes()); }
  private static UsageResponse asUsage(MaterialUsage u) { return new UsageResponse(u.getId(),u.getBatch().getId(),u.getMaterialLot().getId(),u.getQuantityKg()); }
  private static MovementResponse asMovement(InventoryMovement m) { return new MovementResponse(m.getId(),m.getMaterialLot().getId(),m.getChangeKg(),m.getReason(),m.getCreatedAt()); }

  // SUPPLIERS
  @Transactional(readOnly=true) public Page<SupplierResponse> listSuppliers(String search,int page,int size) {
    return suppliers.findAll(contains("name",search),paging(page,size,"name")).map(PlantService::asSupplier);
  }
  @Transactional(readOnly=true) public SupplierResponse getSupplier(UUID id) { return asSupplier(supplier(id)); }
  public SupplierResponse createSupplier(SupplierRequest req) {
    Supplier s=new Supplier();fillSupplier(s,req);return asSupplier(suppliers.saveAndFlush(s));
  }
  public SupplierResponse updateSupplier(UUID id,SupplierRequest req) {
    Supplier s=supplier(id);fillSupplier(s,req);return asSupplier(suppliers.saveAndFlush(s));
  }
  private static void fillSupplier(Supplier s,SupplierRequest r) {
    s.setName(r.name().trim());s.setContactEmail(r.contactEmail().trim().toLowerCase(Locale.ROOT));
    s.setOriginRegion(r.originRegion().trim());s.setActive(r.active());
  }
  public void deleteSupplier(UUID id) { suppliers.delete(supplier(id));suppliers.flush(); }

  // LINES
  @Transactional(readOnly=true) public Page<ProductionLineResponse> listLines(String search,int page,int size) {
    return lines.findAll(contains("name",search),paging(page,size,"code")).map(PlantService::asLine);
  }
  @Transactional(readOnly=true) public ProductionLineResponse getLine(UUID id) { return asLine(line(id)); }
  @Transactional(readOnly=true) @Cacheable("productionLineSummary")
  public List<ProductionLineResponse> lineSummary() { return lines.findAll(Sort.by("code")).stream().map(PlantService::asLine).toList(); }
  @CacheEvict(value="productionLineSummary",allEntries=true)
  public ProductionLineResponse createLine(ProductionLineRequest req) {
    ProductionLine l=new ProductionLine();fillLine(l,req);return asLine(lines.saveAndFlush(l));
  }
  @CacheEvict(value="productionLineSummary",allEntries=true)
  public ProductionLineResponse updateLine(UUID id,ProductionLineRequest req) {
    ProductionLine l=line(id);fillLine(l,req);return asLine(lines.saveAndFlush(l));
  }
  private static void fillLine(ProductionLine l,ProductionLineRequest r) {
    l.setCode(r.code());l.setName(r.name().trim());l.setCapacityKgPerDay(r.capacityKgPerDay());l.setActive(r.active());
  }
  @CacheEvict(value="productionLineSummary",allEntries=true)
  public void deleteLine(UUID id) { lines.delete(line(id));lines.flush(); }

  // RAW MATERIAL LOTS & STOCK LEDGER
  @Transactional(readOnly=true) public Page<MaterialResponse> listMaterials(String search,int page,int size) {
    Specification<RawMaterialLot> visible=contains("materialName",search);
    visible=visible.and((root,query,cb)->cb.isFalse(root.get("archived")));
    return materials.findAll(visible,paging(page,size,"lotCode")).map(PlantService::asMaterial);
  }
  @Transactional(readOnly=true) public MaterialResponse getMaterial(UUID id) { return asMaterial(material(id)); }
  public MaterialResponse createMaterial(MaterialRequest req) {
    RawMaterialLot m=new RawMaterialLot();m.setLotCode(req.lotCode().trim());m.setSupplier(supplier(req.supplierId()));
    m.setMaterialName(req.materialName().trim());m.setStockKg(kg(req.openingStock()));m.setExpiryDate(req.expiryDate());
    materials.saveAndFlush(m);recordMovement(m,m.getStockKg(),"Opening balance");return asMaterial(m);
  }
  public MaterialResponse updateMaterial(UUID id,MaterialUpdateRequest req) {
    RawMaterialLot m=material(id);
    if(m.isArchived()) throw new ConflictException("Archived material lots are read-only");
    if(!m.getLotCode().equals(req.lotCode().trim())) throw new ConflictException("Lot code cannot change once created");
    if(!m.getSupplier().getId().equals(req.supplierId()))
      throw new ConflictException("Material lot supplier is immutable to preserve provenance and inventory history");
    m.setSupplier(supplier(req.supplierId()));m.setMaterialName(req.materialName().trim());m.setExpiryDate(req.expiryDate());
    // Inventory is ONLY modified by stock adjustments or batch consumption.
    return asMaterial(materials.saveAndFlush(m));
  }
  public MaterialResponse adjustStock(UUID id,StockAdjustmentRequest req) {
    RawMaterialLot m=materials.findByIdForUpdate(id).orElseThrow(()->new NoSuchElementException("Raw material lot not found"));
    if(m.isArchived()) throw new ConflictException("Archived material lots cannot be adjusted");
    BigDecimal amount=kg(req.change());if(!req.increase()) amount=amount.negate();
    BigDecimal next=m.getStockKg().add(amount);
    if(next.signum()<0) throw new ConflictException("Insufficient inventory for this adjustment");
    m.setStockKg(next);materials.saveAndFlush(m);recordMovement(m,amount,req.reason());return asMaterial(m);
  }
  private void recordMovement(RawMaterialLot m,BigDecimal change,String reason) {
    InventoryMovement movement=new InventoryMovement();movement.setMaterialLot(m);movement.setChangeKg(change);
    movement.setReason(reason);movement.setCreatedAt(Instant.now());movements.save(movement);
  }
  @Transactional(readOnly=true) public List<MovementResponse> movements(UUID id) {
    material(id);return movements.findByMaterialLotIdOrderByCreatedAtDesc(id).stream().map(PlantService::asMovement).toList();
  }
  public void deleteMaterial(UUID id) {
    RawMaterialLot m=materials.findByIdForUpdate(id).orElseThrow(()->new NoSuchElementException("Raw material lot not found"));
    if(m.getStockKg().signum()!=0)
      throw new ConflictException("Stock balance must be zero before archiving; record a write-off or transfer first");
    m.setArchived(true);materials.saveAndFlush(m);
  }

  // BATCHES
  @Transactional(readOnly=true) public Page<BatchResponse> listBatches(String search,UUID lineId,BatchStatus status,Shift shift,int page,int size) {
    Specification<Batch> spec=contains("batchNumber",search);
    if(lineId!=null) spec=spec.and((root,q,cb)->cb.equal(root.get("productionLine").get("id"),lineId));
    if(status!=null) spec=spec.and((root,q,cb)->cb.equal(root.get("status"),status));
    if(shift!=null) spec=spec.and((root,q,cb)->cb.equal(root.get("shift"),shift));
    return batches.findAll(spec,paging(page,size,"producedAt")).map(PlantService::asBatch);
  }
  @Transactional(readOnly=true) public BatchResponse getBatch(UUID id) { return asBatch(batch(id)); }
  @Transactional(readOnly=true) public List<BatchResponse> byBatchNumber(String number) {
    return batches.findByBatchNumberOrderByProducedAtAsc(number).stream().map(PlantService::asBatch).toList();
  }
  public BatchResponse createBatch(BatchRequest req) {
    Batch b=new Batch();fillBatch(b,req);return asBatch(batches.saveAndFlush(b));
  }
  public BatchResponse updateBatch(UUID id,BatchRequest req) {
    Batch b=batch(id);
    if((usages.existsByBatchId(id) || checks.existsByBatchId(id)) && (
        b.getRawWeightKg().compareTo(kg(req.rawWeight()))!=0 ||
        b.getOutputWeightKg().compareTo(kg(req.outputWeight()))!=0 ||
        !b.getSupplier().getId().equals(req.supplierId()) ||
        !b.getProductionLine().getId().equals(req.productionLineId()) ||
        !b.getBatchNumber().equals(req.batchNumber().trim()) ||
        !b.getProductName().equals(req.productName().trim()) ||
        !b.getProducedAt().equals(req.producedAt()) ||
        !b.getShift().equals(req.shift()) ||
        !b.getMachineCode().equals(req.machineCode().trim())))
      throw new ConflictException("Cannot change audited batch identity, input weight or output weight");
    if(usages.existsByBatchId(id)) {
      BigDecimal alreadyConsumed=usages.findByBatchId(id).stream().map(MaterialUsage::getQuantityKg)
          .reduce(BigDecimal.ZERO,BigDecimal::add);
      if(kg(req.rawWeight()).compareTo(alreadyConsumed)<0)
        throw new ConflictException("Raw input weight cannot be less than recorded material consumption");
    }
    fillBatch(b,req);return asBatch(batches.saveAndFlush(b));
  }
  private void fillBatch(Batch b,BatchRequest r) {
    BigDecimal raw=kg(r.rawWeight()),output=kg(r.outputWeight());
    if(output.compareTo(raw)>0) throw new IllegalArgumentException("Output cannot exceed raw input weight");
    b.setBatchNumber(r.batchNumber().trim());b.setProductName(r.productName().trim());
    ProductionLine productionLine=line(r.productionLineId());
    Supplier supplier=supplier(r.supplierId());
    if(!productionLine.isActive() || !supplier.isActive())
      throw new ConflictException("Cannot use inactive production line or supplier");
    b.setProductionLine(productionLine);b.setSupplier(supplier);
    b.setRawWeightKg(raw);b.setOutputWeightKg(output);b.setProducedAt(r.producedAt());
    b.setShift(r.shift());b.setMoisturePercent(r.moisturePercent());b.setStatus(r.status());
    b.setMachineCode(r.machineCode().trim());b.setNotes(r.notes());
  }
  public void deleteBatch(UUID id) {
    Batch b=batch(id);
    if(usages.existsByBatchId(id) || checks.existsByBatchId(id))
      throw new ConflictException("Cannot delete a batch with traceability history");
    batches.delete(b);batches.flush();
  }

  // QUALITY & MATERIAL CONSUMPTION
  public QualityResponse addQualityCheck(UUID batchId,QualityRequest req) {
    if(req.result()==QualityResult.PASS && req.moisturePercent()==null)
      throw new IllegalArgumentException("PASS quality checks require a recorded moisture value");
    QualityCheck q=new QualityCheck();q.setBatch(batch(batchId));
    q.setCheckedAt(req.checkedAt());q.setMoisturePercent(req.moisturePercent());q.setResult(req.result());q.setNotes(req.notes());
    return asQuality(checks.save(q));
  }
  @Transactional(readOnly=true) public List<QualityResponse> qualityChecks(UUID batchId) {
    batch(batchId);return checks.findByBatchIdOrderByCheckedAtAsc(batchId).stream().map(PlantService::asQuality).toList();
  }
  public UsageResponse consumeMaterial(UUID batchId,UsageRequest req) {
    Batch b=batches.findByIdForUpdate(batchId).orElseThrow(()->new NoSuchElementException("Batch not found"));
    RawMaterialLot m=materials.findByIdForUpdate(req.materialLotId())
        .orElseThrow(()->new NoSuchElementException("Raw material lot not found"));
    if(m.isArchived()) throw new ConflictException("Archived material lots cannot be consumed");
    if(m.getExpiryDate()!=null && m.getExpiryDate().isBefore(java.time.LocalDate.now(java.time.ZoneOffset.UTC)))
      throw new ConflictException("Expired material lots cannot be consumed");
    BigDecimal amount=kg(req.quantity());
    if(!b.getSupplier().getId().equals(m.getSupplier().getId()))
      throw new ConflictException("Material supplier must match batch supplier");
    if(m.getStockKg().compareTo(amount)<0) throw new ConflictException("Insufficient inventory");
    BigDecimal used=usages.findByBatchId(batchId).stream().map(MaterialUsage::getQuantityKg)
        .reduce(BigDecimal.ZERO,BigDecimal::add);
    if(used.add(amount).compareTo(b.getRawWeightKg())>0) throw new ConflictException("Consumption exceeds batch raw weight");
    m.setStockKg(m.getStockKg().subtract(amount));materials.saveAndFlush(m);
    MaterialUsage u=new MaterialUsage();u.setBatch(b);u.setMaterialLot(m);u.setQuantityKg(amount);
    usages.save(u);recordMovement(m,amount.negate(),"Consumed by batch "+b.getId());return asUsage(u);
  }
  @Transactional(readOnly=true) public List<UsageResponse> batchMaterials(UUID batchId) {
    batch(batchId);return usages.findByBatchId(batchId).stream().map(PlantService::asUsage).toList();
  }
  @Transactional(readOnly=true) public TraceResponse trace(UUID id) {
    Batch b=batch(id);
    return new TraceResponse(asBatch(b),asSupplier(b.getSupplier()),asLine(b.getProductionLine()),
        checks.findByBatchIdOrderByCheckedAtAsc(id).stream().map(PlantService::asQuality).toList(),
        usages.findByBatchId(id).stream().map(PlantService::asUsage).toList());
  }
}
