package com.foodplant.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Deterministic unit conversion shared with stock, batches, and quality workflows.
 * No Spring or database dependency so this domain invariant can be compiled/tested offline.
 */
public final class WeightNormalizer {
  private static final BigDecimal GRAMS_PER_KG=new BigDecimal("1000");
  private static final BigDecimal MIN_KG=new BigDecimal("0.001");
  private static final BigDecimal MAX_KG=new BigDecimal("99999999999.999");
  private WeightNormalizer() {}

  public static BigDecimal kilograms(BigDecimal amount,String unit) {
    if(amount==null || unit==null || amount.signum()<=0)
      throw new IllegalArgumentException("Weight must be positive and have a valid unit");
    BigDecimal value;
    switch (unit) {
      case "KG" -> value=amount;
      case "G" -> value=amount.divide(GRAMS_PER_KG,6,RoundingMode.HALF_UP);
      default -> throw new IllegalArgumentException("Weight unit must be KG or G");
    }
    if(value.compareTo(MIN_KG)<0)
      throw new IllegalArgumentException("Weight must be at least 0.001 kg (1 gram)");
    BigDecimal normalized=value.setScale(3,RoundingMode.HALF_UP);
    if(normalized.compareTo(MAX_KG)>0)
      throw new IllegalArgumentException("Weight exceeds maximum supported quantity");
    return normalized;
  }
}
