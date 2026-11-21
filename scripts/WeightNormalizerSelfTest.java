import com.foodplant.util.WeightNormalizer;
import java.math.BigDecimal;

/** Dependency-free domain sanity test. Real JUnit/MockMvc/DB validation remains mandatory. */
public final class WeightNormalizerSelfTest {
  private static int checks=0;
  private static void eq(String expected,String value,String unit) {
    BigDecimal a=WeightNormalizer.kilograms(new BigDecimal(value),unit);
    if(a.compareTo(new BigDecimal(expected))!=0) throw new AssertionError(unit+value+" -> "+a+", expected "+expected);
    checks++;
  }
  private static void reject(String value,String unit) {
    try { WeightNormalizer.kilograms(new BigDecimal(value),unit); }
    catch(IllegalArgumentException good) { checks++;return; }
    throw new AssertionError("Should reject "+value+" "+unit);
  }
  public static void main(String[] ignored) {
    eq("0.001","1","G");eq("0.250","250","G");eq("1.250","1.25","KG");
    eq("0.334","333.6","G");eq("99999999999.999","99999999999.999","KG");
    reject("0","KG");reject("-1","G");reject("0.9","G");reject("0.0009","KG");
    reject("100000000000","KG");reject("1","LB");
    System.out.println("PASS: "+checks+" production WeightNormalizer behavior checks");
  }
}
