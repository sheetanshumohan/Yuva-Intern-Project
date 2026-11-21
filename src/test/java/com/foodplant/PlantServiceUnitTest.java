package com.foodplant;

import com.foodplant.dto.ApiDtos.*;
import com.foodplant.service.PlantService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;

class PlantServiceUnitTest {
  @Test void gramsBecomeKg() { assertEquals(new BigDecimal("0.250"),PlantService.kg(new WeightInput(new BigDecimal("250"),WeightUnit.G))); }
  @Test void kilogramsRetainPrecision() { assertEquals(new BigDecimal("1.250"),PlantService.kg(new WeightInput(new BigDecimal("1.25"),WeightUnit.KG))); }
  @Test void roundToNearestGram() { assertEquals(new BigDecimal("0.334"),PlantService.kg(new WeightInput(new BigDecimal("333.6"),WeightUnit.G))); }
  @Test void rejectNegative() { assertThrows(IllegalArgumentException.class,()->PlantService.kg(new WeightInput(new BigDecimal("-5"),WeightUnit.G))); }
  @Test void rejectZero() { assertThrows(IllegalArgumentException.class,()->PlantService.kg(new WeightInput(BigDecimal.ZERO,WeightUnit.KG))); }
  @Test void rejectSubGram() { assertThrows(IllegalArgumentException.class,()->PlantService.kg(new WeightInput(new BigDecimal("0.1"),WeightUnit.G))); }
  @Test void rejectSubGramThatWouldRoundUp() {
    assertThrows(IllegalArgumentException.class,()->PlantService.kg(new WeightInput(new BigDecimal("0.9"),WeightUnit.G)));
  }
  @Test void rejectHugeQuantity() {
    assertThrows(IllegalArgumentException.class,()->PlantService.kg(new WeightInput(new BigDecimal("100000000000"),WeightUnit.KG)));
  }
  @Test void minimumGramAccepted() {
    assertEquals(new BigDecimal("0.001"),PlantService.kg(new WeightInput(new BigDecimal("1"),WeightUnit.G)));
  }
  @Test void rejectNull() { assertThrows(IllegalArgumentException.class,()->PlantService.kg(null)); }
  @Test void rejectNullUnit() { assertThrows(IllegalArgumentException.class,()->PlantService.kg(new WeightInput(BigDecimal.ONE,null))); }
}
