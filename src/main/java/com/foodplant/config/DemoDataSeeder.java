package com.foodplant.config;

import com.foodplant.entity.*;
import com.foodplant.entity.Models.*;
import com.foodplant.repository.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/** Reproducible synthetic data; clearly marked fictional. Never load in production. */
@Component
public class DemoDataSeeder implements ApplicationRunner {
  private final boolean enabled;
  private final String adminPass,staffPass,viewerPass;
  private final PasswordEncoder passwordEncoder;
  private final UserAccountRepository users;
  private final SupplierRepository suppliers;
  private final ProductionLineRepository lines;
  private final RawMaterialLotRepository materials;
  private final BatchRepository batches;
  private final QualityCheckRepository checks;
  private final MaterialUsageRepository usages;
  private final InventoryMovementRepository movements;
  public DemoDataSeeder(@Value("${app.seed.enabled}") boolean enabled,
        @Value("${app.seed.admin-password}") String adminPass,
        @Value("${app.seed.staff-password}") String staffPass,
        @Value("${app.seed.viewer-password}") String viewerPass,
        PasswordEncoder passwordEncoder,UserAccountRepository users,SupplierRepository suppliers,
        ProductionLineRepository lines,RawMaterialLotRepository materials,BatchRepository batches,
        QualityCheckRepository checks,MaterialUsageRepository usages,InventoryMovementRepository movements) {
    this.enabled=enabled;this.adminPass=adminPass;this.staffPass=staffPass;this.viewerPass=viewerPass;
    this.passwordEncoder=passwordEncoder;this.users=users;this.suppliers=suppliers;
    this.lines=lines;this.materials=materials;this.batches=batches;this.checks=checks;
    this.usages=usages;this.movements=movements;
  }
  @Override @Transactional public void run(ApplicationArguments arguments) {
    if(!enabled) return;
    if(adminPass.isBlank() || staffPass.isBlank() || viewerPass.isBlank())
      throw new IllegalStateException("Seed passwords are required when APP_SEED_ENABLED=true");
    user("admin@foodplant.local",adminPass,Role.ADMIN);
    user("staff@foodplant.local",staffPass,Role.STAFF);
    user("viewer@foodplant.local",viewerPass,Role.VIEWER);
    Supplier s1=supplier("Malabar Spice Growers","supply@malabar.example","Wayanad, Kerala");
    Supplier s2=supplier("Coastal Pepper Collective","pepper@coastal.example","Idukki, Kerala");
    Supplier s3=supplier("Western Ghats Turmeric Co-op","turmeric@ghats.example","Kozhikode, Kerala");
    ProductionLine l1=line("LINE-01","Pepper Cleaning & Milling","1800.000");
    ProductionLine l2=line("LINE-02","Turmeric Grinding","1400.000");
    ProductionLine l3=line("LINE-03","Cardamom Blending & Packing","950.000");
    RawMaterialLot pepper=lot("RAW-PEPPER-001",s2,"Black Pepper","875500 G");
    RawMaterialLot turmeric=lot("RAW-TURMERIC-001",s3,"Turmeric Rhizome","1200 KG");
    RawMaterialLot cardamom=lot("RAW-CARDAMOM-001",s1,"Green Cardamom","425250 G");
    // Same legacy batch number occurs on same production line across reset dates: UUID is the real PK.
    Batch b1=batch("B-001",l1,s2,"Ground Black Pepper","500000 G","470 KG","2026-10-01T08:30:00Z",Shift.DAY,"9.70",BatchStatus.COMPLETED,"MILL-1");
    Batch b2=batch("B-001",l1,s2,"Ground Black Pepper","350 KG","330000 G","2026-11-07T22:10:00Z",Shift.NIGHT,null,BatchStatus.QUARANTINED,"MILL-2");
    Batch b3=batch("T-028",l2,s3,"Turmeric Powder","450 KG","420 KG","2026-11-08T14:15:00Z",Shift.EVENING,"8.40",BatchStatus.COMPLETED,"GRINDER-3");
    Batch b4=batch("C-017",l3,s1,"Cardamom Mix","240000 G","230000 G","2026-11-09T23:45:00Z",Shift.NIGHT,null,BatchStatus.IN_PROGRESS,"BLENDER-1");
    Batch b5=batch("P-062",l1,s2,"Premium Pepper","280 KG","264 KG","2026-11-10T07:30:00Z",Shift.DAY,"10.10",BatchStatus.COMPLETED,"MILL-1");
    quality(b1,"2026-10-01T11:30:00Z","9.70",QualityResult.PASS,"Moisture within spec");
    quality(b2,"2026-11-07T23:55:00Z",null,QualityResult.PENDING,"Night-shift moisture reading missing");
    quality(b3,"2026-11-08T17:10:00Z","8.40",QualityResult.PASS,"Colour and moisture acceptable");
    quality(b4,"2026-11-10T00:30:00Z",null,QualityResult.PENDING,"Lab unavailable during night shift");
    quality(b5,"2026-11-10T10:45:00Z","10.10",QualityResult.PASS,"Final quality released");
    // Synthetic usage events remain below recorded raw batch weight and starting lot balances.
    // Each is idempotent; stock, material usage and inventory ledger are in one transaction.
    seedUsage(b1,pepper,"350.000");
    seedUsage(b2,pepper,"250.000");
    seedUsage(b3,turmeric,"450.000");
    seedUsage(b4,cardamom,"240.000");
    seedUsage(b5,pepper,"200.000");
  }
  private static BigDecimal seedWeight(String weightAndUnit) {
    String[] parts=weightAndUnit.split(" ");
    BigDecimal value=new BigDecimal(parts[0]);
    return "G".equals(parts[1]) ? value.divide(new BigDecimal("1000"),3,java.math.RoundingMode.HALF_UP)
                               : value.setScale(3,java.math.RoundingMode.HALF_UP);
  }
  private void user(String email,String pass,Role role) {
    if(users.findByEmailIgnoreCase(email).isPresent()) return;
    UserAccount u=new UserAccount();u.setEmail(email);u.setPasswordHash(passwordEncoder.encode(pass));u.setRole(role);users.save(u);
  }
  private Supplier supplier(String name,String email,String origin) {
    Optional<Supplier> existing=suppliers.findAll().stream().filter(x->x.getName().equals(name)).findFirst();
    if(existing.isPresent()) return existing.get();
    Supplier s=new Supplier();s.setName(name);s.setContactEmail(email);s.setOriginRegion(origin);s.setActive(true);
    return suppliers.save(s);
  }
  private ProductionLine line(String code,String name,String capacity) {
    return lines.findByCode(code).orElseGet(()->{
      ProductionLine l=new ProductionLine();l.setCode(code);l.setName(name);
      l.setCapacityKgPerDay(new BigDecimal(capacity));l.setActive(true);return lines.save(l);
    });
  }
  private RawMaterialLot lot(String code,Supplier supplier,String name,String openingKg) {
    return materials.findByLotCode(code).orElseGet(()->{
      RawMaterialLot m=new RawMaterialLot();m.setLotCode(code);m.setSupplier(supplier);
      m.setMaterialName(name);m.setStockKg(seedWeight(openingKg));m=materials.save(m);
      movement(m,m.getStockKg(),"Opening balance (synthetic seed)");
      return m;
    });
  }
  private Batch batch(String code,ProductionLine line,Supplier supplier,String product,
                      String raw,String output,String produced,Shift shift,String moisture,
                      BatchStatus status,String machine) {
    Instant timestamp=Instant.parse(produced);
    Optional<Batch> existing=batches.findByBatchNumberOrderByProducedAtAsc(code).stream()
        .filter(x->x.getProducedAt().equals(timestamp)).findFirst();
    if(existing.isPresent()) return existing.get();
    Batch b=new Batch();b.setBatchNumber(code);b.setProductionLine(line);b.setSupplier(supplier);b.setProductName(product);
    b.setRawWeightKg(seedWeight(raw));b.setOutputWeightKg(seedWeight(output));
    b.setProducedAt(timestamp);b.setShift(shift);
    b.setMoisturePercent(moisture==null?null:new BigDecimal(moisture));b.setStatus(status);b.setMachineCode(machine);
    b.setNotes(moisture==null?"Missing night-shift moisture data":"Production record imported");
    return batches.save(b);
  }
  private void movement(RawMaterialLot lot,BigDecimal change,String reason) {
    InventoryMovement movement=new InventoryMovement();movement.setMaterialLot(lot);
    movement.setChangeKg(change);movement.setReason(reason);movement.setCreatedAt(Instant.now());
    movements.save(movement);
  }
  private void seedUsage(Batch batch,RawMaterialLot lot,String kg) {
    if(usages.existsByBatchId(batch.getId())) return;
    BigDecimal qty=new BigDecimal(kg);
    if(lot.getStockKg().compareTo(qty)<0)
      throw new IllegalStateException("Insufficient synthetic seed inventory for batch "+batch.getBatchNumber());
    MaterialUsage usage=new MaterialUsage();usage.setBatch(batch);usage.setMaterialLot(lot);
    usage.setQuantityKg(qty);usages.save(usage);
    lot.setStockKg(lot.getStockKg().subtract(qty));materials.save(lot);
    movement(lot,qty.negate(),"Consumption for seeded batch "+batch.getId());
  }
  private void quality(Batch batch,String date,String moisture,QualityResult result,String notes) {
    if(checks.existsByBatchId(batch.getId())) return;
    QualityCheck q=new QualityCheck();q.setBatch(batch);q.setCheckedAt(Instant.parse(date));
    q.setMoisturePercent(moisture==null?null:new BigDecimal(moisture));q.setResult(result);q.setNotes(notes);
    checks.save(q);
  }
}
