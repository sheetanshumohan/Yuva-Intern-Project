package com.foodplant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @ActiveProfiles("test") @TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ApiIntegrationTest {
  @Autowired MockMvc mvc;
  @Autowired ObjectMapper mapper;
  private String admin,staff,viewer;

  @BeforeAll void authenticate() throws Exception {
    admin=login("admin@foodplant.local","TestAdminPassword123!");
    staff=login("staff@foodplant.local","TestStaffPassword123!");
    viewer=login("viewer@foodplant.local","TestViewerPassword123!");
  }
  private String login(String email,String password) throws Exception {
    var response=mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
        .content(mapper.writeValueAsBytes(Map.of("email",email,"password",password))))
        .andExpect(status().isOk()).andReturn().getResponse();
    return mapper.readTree(response.getContentAsString()).get("accessToken").asText();
  }
  private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder r,String token) {
    return r.header("Authorization","Bearer "+token);
  }
  private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder r,String token,String body) {
    return auth(r,token).contentType(MediaType.APPLICATION_JSON).content(body);
  }
  private String supplierPayload(String name) {
    return "{\"name\":\""+name+"\",\"contactEmail\":\"hello@co-op.example\",\"originRegion\":\"Kerala\",\"active\":true}";
  }
  private String linePayload(String code,String name) {
    return "{\"code\":\""+code+"\",\"name\":\""+name+"\",\"capacityKgPerDay\":600,\"active\":true}";
  }
  private String batchPayload(String lineId,String supplierId,String batchNumber,String rawWeight,String outputWeight,String moisture) {
    return "{\"batchNumber\":\""+batchNumber+"\",\"productName\":\"Test pepper\",\"productionLineId\":\""+lineId+
      "\",\"supplierId\":\""+supplierId+"\",\"rawWeight\":"+rawWeight+",\"outputWeight\":"+outputWeight+
      ",\"producedAt\":\"2026-11-12T21:45:00Z\",\"shift\":\"NIGHT\",\"moisturePercent\":"+moisture+
      ",\"status\":\"COMPLETED\",\"machineCode\":\"TEST-MILL\"}";
  }
  private JsonNode parse(String body) throws Exception { return mapper.readTree(body); }
  private String body(MockHttpServletRequestBuilder request) throws Exception {
    return mvc.perform(request).andReturn().getResponse().getContentAsString();
  }
  private String firstLineId() throws Exception {
    return parse(body(auth(get("/api/v1/production-lines"),admin))).get("content").get(0).get("id").asText();
  }
  private String firstSupplierId() throws Exception {
    return parse(body(auth(get("/api/v1/suppliers"),admin))).get("content").get(0).get("id").asText();
  }
  private String createBatch(String batchNumber,String raw,String output,String moisture) throws Exception {
    return parse(body(json(post("/api/v1/batches"),staff,batchPayload(firstLineId(),firstSupplierId(),batchNumber,raw,output,moisture))))
        .get("id").asText();
  }
  private String createMaterial(String lot,String supplier,String stock) throws Exception {
    return parse(body(json(post("/api/v1/raw-materials"),staff,"{\"lotCode\":\""+lot+
        "\",\"supplierId\":\""+supplier+"\",\"materialName\":\"Test pepper\",\"openingStock\":"+stock+"}")))
        .get("id").asText();
  }

  @Test void anonymousProtected() throws Exception { mvc.perform(get("/api/v1/batches")).andExpect(status().isUnauthorized()); }
  @Test void publicHealth() throws Exception { mvc.perform(get("/actuator/health")).andExpect(status().isOk()); }
  @Test void loginWrongPassword() throws Exception { mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"admin@foodplant.local\",\"password\":\"wrong\"}")) .andExpect(status().isUnauthorized()); }
  @Test void loginMalformed() throws Exception { mvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"invalid\",\"password\":\"\"}")) .andExpect(status().isBadRequest()); }
  @Test void viewerCanRead() throws Exception { mvc.perform(auth(get("/api/v1/batches"),viewer)).andExpect(status().isOk()); }
  @Test void viewerCannotWrite() throws Exception { mvc.perform(json(post("/api/v1/suppliers"),viewer,supplierPayload("Blocked"))).andExpect(status().isForbidden()); }
  @Test void staffCannotDelete() throws Exception { mvc.perform(auth(delete("/api/v1/production-lines/"+UUID.randomUUID()),staff)).andExpect(status().isForbidden()); }
  @Test void staffCannotCreateUsers() throws Exception { mvc.perform(json(post("/api/v1/auth/users"),staff,"{\"email\":\"hi@example.com\",\"password\":\"Password123456!\",\"role\":\"ADMIN\"}")) .andExpect(status().isForbidden()); }
  @Test void adminCanListUsers() throws Exception { mvc.perform(auth(get("/api/v1/auth/users"),admin)).andExpect(status().isOk()).andExpect(jsonPath("$[0].email").exists()); }
  @Test @Transactional void adminCanCreateViewer() throws Exception { mvc.perform(json(post("/api/v1/auth/users"),admin,"{\"email\":\"new@demo.example\",\"password\":\"Password123456!\",\"role\":\"VIEWER\"}")) .andExpect(status().isCreated()); }
  @Test @Transactional void rejectDuplicateUser() throws Exception { mvc.perform(json(post("/api/v1/auth/users"),admin,"{\"email\":\"admin@foodplant.local\",\"password\":\"Password123456!\",\"role\":\"VIEWER\"}")) .andExpect(status().isConflict()); }
  @Test void emptySuppliersSearch() throws Exception { mvc.perform(auth(get("/api/v1/suppliers?search=unfindable-xyz"),staff)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0)); }
  @Test void supplierPage() throws Exception { mvc.perform(auth(get("/api/v1/suppliers?page=0&size=2"),viewer)).andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2)); }
  @Test void invalidPage() throws Exception { mvc.perform(auth(get("/api/v1/suppliers?page=-1"),viewer)).andExpect(status().isBadRequest()); }
  @Test void oversizedPage() throws Exception { mvc.perform(auth(get("/api/v1/batches?size=1000"),viewer)).andExpect(status().isBadRequest()); }
  @Test void notFoundSupplier() throws Exception { mvc.perform(auth(get("/api/v1/suppliers/"+UUID.randomUUID()),staff)).andExpect(status().isNotFound()); }
  @Test void rejectInvalidSupplier() throws Exception { mvc.perform(json(post("/api/v1/suppliers"),staff,"{\"name\":\"\",\"contactEmail\":\"invalid\"}")) .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.name").exists()); }
  @Test @Transactional void supplierCrud() throws Exception {
    String id=parse(body(json(post("/api/v1/suppliers"),staff,supplierPayload("New-Grower")))).get("id").asText();
    mvc.perform(auth(get("/api/v1/suppliers/"+id),staff)).andExpect(status().isOk()).andExpect(jsonPath("$.name").value("New-Grower"));
    mvc.perform(json(put("/api/v1/suppliers/"+id),staff,supplierPayload("New-Grower-Updated"))).andExpect(status().isOk());
    mvc.perform(auth(delete("/api/v1/suppliers/"+id),admin)).andExpect(status().isNoContent());
    mvc.perform(auth(get("/api/v1/suppliers/"+id),admin)).andExpect(status().isNotFound());
  }
  @Test @Transactional void duplicateSupplierConflict() throws Exception { mvc.perform(json(post("/api/v1/suppliers"),admin,supplierPayload("Malabar Spice Growers"))).andExpect(status().isConflict()); }
  @Test void lineSummaryContainsThree() throws Exception { mvc.perform(auth(get("/api/v1/production-lines/summary"),viewer)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(3)); }
  @Test void lineFilter() throws Exception { mvc.perform(auth(get("/api/v1/production-lines?search=Grinding"),viewer)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)); }
  @Test void notFoundLine() throws Exception { mvc.perform(auth(get("/api/v1/production-lines/"+UUID.randomUUID()),viewer)).andExpect(status().isNotFound()); }
  @Test @Transactional void lineCrudAndSummaryInvalidation() throws Exception {
    String id=parse(body(json(post("/api/v1/production-lines"),staff,linePayload("LINE-99","Pilot Line")))).get("id").asText();
    mvc.perform(auth(get("/api/v1/production-lines/summary"),admin)).andExpect(jsonPath("$.length()").value(4));
    mvc.perform(json(put("/api/v1/production-lines/"+id),staff,linePayload("LINE-99","Pilot Line Updated"))).andExpect(status().isOk());
    mvc.perform(auth(delete("/api/v1/production-lines/"+id),admin)).andExpect(status().isNoContent());
  }
  @Test void seededDuplicateBatchNumberSurvivesReset() throws Exception { mvc.perform(auth(get("/api/v1/batches/lookup?batchNumber=B-001"),viewer)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2)); }
  @Test void nightShiftMissingMoistureIsNull() throws Exception { mvc.perform(auth(get("/api/v1/batches?shift=NIGHT"),viewer)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2)); }
  @Test void batchStatusFilter() throws Exception { mvc.perform(auth(get("/api/v1/batches?status=QUARANTINED"),viewer)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)); }
  @Test void unknownStatusGets400() throws Exception { mvc.perform(auth(get("/api/v1/batches?status=DOES_NOT_EXIST"),viewer)).andExpect(status().isBadRequest()); }
  @Test @Transactional void createBatchWithMixedUnitsAndMissingMoisture() throws Exception {
    String id=createBatch("TEST-MIXED","{\"value\":1500,\"unit\":\"G\"}","{\"value\":1.2,\"unit\":\"KG\"}","null");
    mvc.perform(auth(get("/api/v1/batches/"+id),staff)).andExpect(status().isOk())
      .andExpect(jsonPath("$.rawWeightKg").value(1.5)).andExpect(jsonPath("$.moisturePercent").doesNotExist());
  }
  @Test @Transactional void batchCreateAndDelete() throws Exception {
    String id=createBatch("DELETE-TEST","{\"value\":8,\"unit\":\"KG\"}","{\"value\":7,\"unit\":\"KG\"}","9.5");
    mvc.perform(auth(delete("/api/v1/batches/"+id),admin)).andExpect(status().isNoContent());
    mvc.perform(auth(get("/api/v1/batches/"+id),admin)).andExpect(status().isNotFound());
  }
  @Test void rejectOutputHeavierThanInput() throws Exception {
    mvc.perform(json(post("/api/v1/batches"),staff,batchPayload(firstLineId(),firstSupplierId(),"BAD",
      "{\"value\":1,\"unit\":\"KG\"}","{\"value\":2,\"unit\":\"KG\"}","10.0"))).andExpect(status().isBadRequest());
  }
  @Test void batchNotFound() throws Exception { mvc.perform(auth(get("/api/v1/batches/"+UUID.randomUUID()+"/trace"),viewer)).andExpect(status().isNotFound()); }
  @Test @Transactional void qualityAndTrace() throws Exception {
    String id=createBatch("QC-TEST","{\"value\":10,\"unit\":\"KG\"}","{\"value\":9,\"unit\":\"KG\"}","null");
    mvc.perform(json(post("/api/v1/batches/"+id+"/quality-checks"),staff,
      "{\"checkedAt\":\"2026-11-13T00:00:00Z\",\"moisturePercent\":null,\"result\":\"PENDING\",\"notes\":\"Night shift\"}"))
      .andExpect(status().isCreated());
    mvc.perform(auth(get("/api/v1/batches/"+id+"/trace"),viewer)).andExpect(status().isOk())
      .andExpect(jsonPath("$.qualityChecks.length()").value(1));
    mvc.perform(auth(delete("/api/v1/batches/"+id),admin)).andExpect(status().isConflict());
  }
  @Test void invalidQualityData() throws Exception { mvc.perform(json(post("/api/v1/batches/"+UUID.randomUUID()+"/quality-checks"),staff,"{\"moisturePercent\":110,\"result\":\"PASS\"}")) .andExpect(status().isBadRequest()); }
  @Test void defaultBatchTrace() throws Exception {
    JsonNode first=parse(body(auth(get("/api/v1/batches"),viewer))).get("content").get(0);
    mvc.perform(auth(get("/api/v1/batches/"+first.get("id").asText()+"/trace"),viewer))
      .andExpect(status().isOk()).andExpect(jsonPath("$.supplier.name").exists()).andExpect(jsonPath("$.productionLine.code").exists());
  }
  @Test void seededBatchTraceContainsActualStockUsage() throws Exception {
    var duplicates=parse(body(auth(get("/api/v1/batches/lookup?batchNumber=B-001"),viewer)));
    String id=duplicates.get(0).get("id").asText();
    mvc.perform(auth(get("/api/v1/batches/"+id+"/trace"),viewer))
      .andExpect(status().isOk()).andExpect(jsonPath("$.materials.length()").value(1));
  }
  @Test void seededMaterialsExist() throws Exception { mvc.perform(auth(get("/api/v1/raw-materials"),viewer)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3)); }
  @Test void notFoundMaterial() throws Exception { mvc.perform(auth(get("/api/v1/raw-materials/"+UUID.randomUUID()),viewer)).andExpect(status().isNotFound()); }
  @Test @Transactional void materialCreateAndStockAdjust() throws Exception {
    String id=createMaterial("TEST-LOT-NEW",firstSupplierId(),"{\"value\":2500,\"unit\":\"G\"}");
    mvc.perform(auth(get("/api/v1/raw-materials/"+id),viewer)).andExpect(status().isOk()).andExpect(jsonPath("$.stockKg").value(2.5));
    mvc.perform(json(post("/api/v1/raw-materials/"+id+"/adjustments"),staff,
      "{\"change\":{\"value\":500,\"unit\":\"G\"},\"reason\":\"Restock\",\"increase\":true}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.stockKg").value(3.0));
    mvc.perform(auth(get("/api/v1/raw-materials/"+id+"/movements"),viewer)).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    mvc.perform(auth(delete("/api/v1/raw-materials/"+id),admin)).andExpect(status().isConflict());
    mvc.perform(json(post("/api/v1/raw-materials/"+id+"/adjustments"),staff,
      "{\"change\":{\"value\":3,\"unit\":\"KG\"},\"reason\":\"Audited write-off\",\"increase\":false}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.stockKg").value(0.0));
    mvc.perform(auth(delete("/api/v1/raw-materials/"+id),admin)).andExpect(status().isNoContent());
    mvc.perform(auth(get("/api/v1/raw-materials/"+id),viewer)).andExpect(status().isOk())
      .andExpect(jsonPath("$.archived").value(true));
    mvc.perform(json(post("/api/v1/raw-materials/"+id+"/adjustments"),staff,
      "{\"change\":{\"value\":1,\"unit\":\"KG\"},\"reason\":\"Invalid\",\"increase\":true}"))
      .andExpect(status().isConflict());
  }
  @Test @Transactional void rejectNegativeStock() throws Exception {
    String id=createMaterial("NEGATIVE-STOCK-TEST",firstSupplierId(),"{\"value\":1,\"unit\":\"KG\"}");
    mvc.perform(json(post("/api/v1/raw-materials/"+id+"/adjustments"),staff,
      "{\"change\":{\"value\":2,\"unit\":\"KG\"},\"reason\":\"Too much\",\"increase\":false}"))
      .andExpect(status().isConflict());
  }
  @Test @Transactional void materialConsumptionUpdatesStockAndTrace() throws Exception {
    String supplier=firstSupplierId(),lot=createMaterial("CONSUME-LOT",supplier,"{\"value\":20,\"unit\":\"KG\"}");
    String id=parse(body(json(post("/api/v1/batches"),staff,batchPayload(firstLineId(),supplier,"CONSUME-TEST",
      "{\"value\":10,\"unit\":\"KG\"}","{\"value\":9,\"unit\":\"KG\"}","null")))).get("id").asText();
    mvc.perform(json(post("/api/v1/batches/"+id+"/materials"),staff,"{\"materialLotId\":\""+lot+"\",\"quantity\":{\"value\":1000,\"unit\":\"G\"}}"))
      .andExpect(status().isCreated()).andExpect(jsonPath("$.quantityKg").value(1.0));
    mvc.perform(auth(get("/api/v1/raw-materials/"+lot),viewer)).andExpect(jsonPath("$.stockKg").value(19.0));
    mvc.perform(auth(get("/api/v1/batches/"+id+"/trace"),viewer)).andExpect(jsonPath("$.materials.length()").value(1));
  }
  @Test @Transactional void cannotConsumeAboveBatchWeight() throws Exception {
    String supplier=firstSupplierId(),lot=createMaterial("EXCESS-LOT",supplier,"{\"value\":100,\"unit\":\"KG\"}");
    String id=createBatch("OVER-CONSUME","{\"value\":10,\"unit\":\"KG\"}","{\"value\":9,\"unit\":\"KG\"}","null");
    mvc.perform(json(post("/api/v1/batches/"+id+"/materials"),staff,"{\"materialLotId\":\""+lot+"\",\"quantity\":{\"value\":12,\"unit\":\"KG\"}}"))
      .andExpect(status().isConflict());
  }
  @Test @Transactional void cannotConsumeAboveStock() throws Exception {
    String supplier=firstSupplierId(),lot=createMaterial("LOW-LOT",supplier,"{\"value\":1,\"unit\":\"KG\"}");
    String id=createBatch("LOW-TEST","{\"value\":10,\"unit\":\"KG\"}","{\"value\":9,\"unit\":\"KG\"}","null");
    mvc.perform(json(post("/api/v1/batches/"+id+"/materials"),staff,"{\"materialLotId\":\""+lot+"\",\"quantity\":{\"value\":2,\"unit\":\"KG\"}}"))
      .andExpect(status().isConflict());
  }
  @Test void unauthorizedAccessDeniedAfterTampering() throws Exception { mvc.perform(get("/api/v1/suppliers").header("Authorization","Bearer invalid.jwt.signature")).andExpect(status().isUnauthorized()); }
  @Test void noRawServerErrorOnInvalidJson() throws Exception { mvc.perform(json(post("/api/v1/batches"),staff,"{")) .andExpect(status().isBadRequest()); }

  @Test @Transactional void updateMaterialMetadataNotStock() throws Exception {
    String supplier=firstSupplierId();
    String id=createMaterial("EDITABLE-LOT",supplier,"{\"value\":5,\"unit\":\"KG\"}");
    mvc.perform(json(put("/api/v1/raw-materials/"+id),staff,
      "{\"lotCode\":\"EDITABLE-LOT\",\"supplierId\":\""+supplier+
      "\",\"materialName\":\"Renamed pepper\"}"))
      .andExpect(status().isOk()).andExpect(jsonPath("$.stockKg").value(5.0))
      .andExpect(jsonPath("$.materialName").value("Renamed pepper"));
  }
  @Test @Transactional void preventMaterialLotCodeMutation() throws Exception {
    String supplier=firstSupplierId(),id=createMaterial("FIXED-LOT",supplier,"{\"value\":5,\"unit\":\"KG\"}");
    mvc.perform(json(put("/api/v1/raw-materials/"+id),staff,
      "{\"lotCode\":\"NEW-CODE\",\"supplierId\":\""+supplier+
      "\",\"materialName\":\"Pepper\"}"))
      .andExpect(status().isConflict());
  }
  @Test @Transactional void preventDuplicateLot() throws Exception {
    String supplier=firstSupplierId();
    createMaterial("DUP-LOT",supplier,"{\"value\":2,\"unit\":\"KG\"}");
    mvc.perform(json(post("/api/v1/raw-materials"),staff,
      "{\"lotCode\":\"DUP-LOT\",\"supplierId\":\""+supplier+
      "\",\"materialName\":\"Pepper\",\"openingStock\":{\"value\":2,\"unit\":\"KG\"}}"))
      .andExpect(status().isConflict());
  }
  @Test @Transactional void rejectConsumptionFromOtherSupplier() throws Exception {
    JsonNode suppliers=parse(body(auth(get("/api/v1/suppliers?size=10"),viewer))).get("content");
    String supplier=firstSupplierId();
    String other=null;
    for(JsonNode s:suppliers) if(!s.get("id").asText().equals(supplier)) { other=s.get("id").asText();break; }
    assertNotNull(other);
    String lot=createMaterial("OTHER-SUPPLIER-LOT",other,"{\"value\":20,\"unit\":\"KG\"}");
    String id=createBatch("OTHER-SUPPLIER-BATCH","{\"value\":10,\"unit\":\"KG\"}","{\"value\":9,\"unit\":\"KG\"}","null");
    mvc.perform(json(post("/api/v1/batches/"+id+"/materials"),staff,
      "{\"materialLotId\":\""+lot+"\",\"quantity\":{\"value\":1,\"unit\":\"KG\"}}"))
      .andExpect(status().isConflict());
  }
  @Test @Transactional void batchUpdateAndSearch() throws Exception {
    String id=createBatch("UPDATE-KEY","{\"value\":10,\"unit\":\"KG\"}","{\"value\":8,\"unit\":\"KG\"}","null");
    mvc.perform(json(put("/api/v1/batches/"+id),staff,
      batchPayload(firstLineId(),firstSupplierId(),"UPDATED-KEY","{\"value\":10,\"unit\":\"KG\"}",
        "{\"value\":8,\"unit\":\"KG\"}","9.1")))
      .andExpect(status().isOk()).andExpect(jsonPath("$.batchNumber").value("UPDATED-KEY"));
    mvc.perform(auth(get("/api/v1/batches?search=UPDATED-KEY"),viewer))
      .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
  }
  @Test void rejectInvalidBatchSupplierId() throws Exception {
    mvc.perform(json(post("/api/v1/batches"),staff,
      batchPayload(firstLineId(),UUID.randomUUID().toString(),"INVALID-REFERENCE",
       "{\"value\":10,\"unit\":\"KG\"}","{\"value\":9,\"unit\":\"KG\"}","null")))
      .andExpect(status().isNotFound());
  }
  @Test void rejectMalformedBatchId() throws Exception {
    mvc.perform(auth(get("/api/v1/batches/not-a-uuid"),viewer)).andExpect(status().isBadRequest());
  }
  @Test void rawMaterialFilter() throws Exception {
    mvc.perform(auth(get("/api/v1/raw-materials?search=Turmeric"),viewer)).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
  }
  @Test void missingBatchQualityCheck() throws Exception {
    mvc.perform(auth(get("/api/v1/batches/"+UUID.randomUUID()+"/quality-checks"),viewer)).andExpect(status().isNotFound());
  }
  @Test void missingBatchMaterialUsage() throws Exception {
    mvc.perform(auth(get("/api/v1/batches/"+UUID.randomUUID()+"/materials"),viewer)).andExpect(status().isNotFound());
  }
  @Test @Transactional void removeStockWithLedger() throws Exception {
    String lot=createMaterial("REDUCE-LOT",firstSupplierId(),"{\"value\":10,\"unit\":\"KG\"}");
    mvc.perform(json(post("/api/v1/raw-materials/"+lot+"/adjustments"),staff,
       "{\"change\":{\"value\":1.5,\"unit\":\"KG\"},\"reason\":\"Waste\",\"increase\":false}"))
       .andExpect(status().isOk()).andExpect(jsonPath("$.stockKg").value(8.5));
    mvc.perform(auth(get("/api/v1/raw-materials/"+lot+"/movements"),viewer)).andExpect(jsonPath("$[0].changeKg").value(-1.5));
  }
  @Test void bodyMissingRequiredFields() throws Exception {
    mvc.perform(json(post("/api/v1/batches"),staff,"{}"))
      .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.machineCode").exists());
  }
  @Test @Transactional void unknownFieldIsRejectedInsteadOfSilentlyIgnored() throws Exception {
    String supplier=firstSupplierId();
    String id=createMaterial("UNKNOWN-PROPERTY-LOT",supplier,"{\"value\":5,\"unit\":\"KG\"}");
    mvc.perform(json(put("/api/v1/raw-materials/"+id),staff,
      "{\"lotCode\":\"UNKNOWN-PROPERTY-LOT\",\"supplierId\":\""+supplier+
      "\",\"materialName\":\"Pepper\",\"openingStock\":{\"value\":999,\"unit\":\"KG\"}}"))
      .andExpect(status().isBadRequest());
  }
  @Test void rejectMissingStockAdjustmentDirection() throws Exception {
    mvc.perform(json(post("/api/v1/raw-materials/"+UUID.randomUUID()+"/adjustments"),staff,
      "{\"change\":{\"value\":1,\"unit\":\"KG\"},\"reason\":\"Unknown direction\"}"))
      .andExpect(status().isBadRequest());
  }
  @Test void rejectMissingActiveOnSupplier() throws Exception {
    mvc.perform(json(post("/api/v1/suppliers"),staff,
      "{\"name\":\"Missing Flag\",\"contactEmail\":\"sample@example.test\",\"originRegion\":\"Kerala\"}"))
      .andExpect(status().isBadRequest());
  }
  @Test void rejectMissingMoistureOnPassingQA() throws Exception {
    mvc.perform(json(post("/api/v1/batches/"+UUID.randomUUID()+"/quality-checks"),staff,
      "{\"checkedAt\":\"2026-11-13T00:00:00Z\",\"moisturePercent\":null,\"result\":\"PASS\"}"))
      .andExpect(status().isBadRequest());
  }
  @Test @Transactional void rejectExpiredMaterialConsumption() throws Exception {
    String supplier=firstSupplierId();
    String lot=parse(body(json(post("/api/v1/raw-materials"),staff,
      "{\"lotCode\":\"EXPIRED-LOT\",\"supplierId\":\""+supplier+
      "\",\"materialName\":\"Expired raw spice\",\"openingStock\":{\"value\":10,\"unit\":\"KG\"},\"expiryDate\":\"2020-01-01\"}"))).get("id").asText();
    String batch=createBatch("EXPIRED-BATCH","{\"value\":5,\"unit\":\"KG\"}","{\"value\":4,\"unit\":\"KG\"}","null");
    mvc.perform(json(post("/api/v1/batches/"+batch+"/materials"),staff,
      "{\"materialLotId\":\""+lot+"\",\"quantity\":{\"value\":1,\"unit\":\"KG\"}}"))
      .andExpect(status().isConflict());
  }
  @Test @Transactional void supplierOfMaterialLotIsImmutable() throws Exception {
    JsonNode page=parse(body(auth(get("/api/v1/suppliers?size=10"),viewer))).get("content");
    String original=page.get(0).get("id").asText();
    String other=page.get(1).get("id").asText();
    String lot=createMaterial("IMMUTABLE-SUPPLIER-LOT",original,"{\"value\":5,\"unit\":\"KG\"}");
    mvc.perform(json(put("/api/v1/raw-materials/"+lot),staff,
      "{\"lotCode\":\"IMMUTABLE-SUPPLIER-LOT\",\"supplierId\":\""+other+
      "\",\"materialName\":\"Reassigned\"}"))
      .andExpect(status().isConflict());
    mvc.perform(auth(get("/api/v1/raw-materials/"+lot),viewer))
      .andExpect(jsonPath("$.supplierId").value(original));
  }
  @Test @Transactional void auditedBatchCannotChangeOutputWeight() throws Exception {
    String line=firstLineId(),supplier=firstSupplierId();
    String id=createBatch("OUTPUT-AUDIT-TEST","{\"value\":10,\"unit\":\"KG\"}",
      "{\"value\":8,\"unit\":\"KG\"}","null");
    mvc.perform(json(post("/api/v1/batches/"+id+"/quality-checks"),staff,
      "{\"checkedAt\":\"2026-11-21T00:00:00Z\",\"result\":\"PENDING\"}"))
      .andExpect(status().isCreated());
    mvc.perform(json(put("/api/v1/batches/"+id),staff,batchPayload(line,supplier,"OUTPUT-AUDIT-TEST",
      "{\"value\":10,\"unit\":\"KG\"}","{\"value\":9,\"unit\":\"KG\"}","null")))
      .andExpect(status().isConflict());
  }
  @Test void moistureFractionAboveTwoDecimalsIsRejected() throws Exception {
    mvc.perform(json(post("/api/v1/batches"),staff,batchPayload(firstLineId(),firstSupplierId(),
      "BAD-MOISTURE","{\"value\":10,\"unit\":\"KG\"}","{\"value\":9,\"unit\":\"KG\"}","9.123")))
      .andExpect(status().isBadRequest());
  }
  @Test void malformedAuthenticationTokenIsUnauthorized() throws Exception {
    mvc.perform(get("/api/v1/production-lines/summary")
      .header("Authorization","Bearer tampered.not-a-jwt.signature"))
      .andExpect(status().isUnauthorized());
  }
  @Test void negativeMaterialPageSizeIsRejected() throws Exception {
    mvc.perform(auth(get("/api/v1/raw-materials?size=-5"),viewer))
      .andExpect(status().isBadRequest());
  }
  @Test void pathTraversalIsNotRawError() throws Exception {
    mvc.perform(auth(get("/api/v1/does-not-exist"),viewer)).andExpect(status().isNotFound());
  }
}
