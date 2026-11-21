package com.foodplant;

import com.foodplant.config.RateLimitFilter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import static org.junit.jupiter.api.Assertions.*;

class RateLimitFilterUnitTest {
  @Test void loginRateLimitReturns429() throws Exception {
    RateLimitFilter filter=new RateLimitFilter(100,1);
    MockHttpServletRequest one=new MockHttpServletRequest("POST","/api/v1/auth/login");
    one.setRemoteAddr("203.0.113.4");
    MockHttpServletResponse allowed=new MockHttpServletResponse();
    filter.doFilter(one,allowed,new MockFilterChain());
    assertEquals(200,allowed.getStatus());
    MockHttpServletRequest two=new MockHttpServletRequest("POST","/api/v1/auth/login");
    two.setRemoteAddr("203.0.113.4");
    MockHttpServletResponse denied=new MockHttpServletResponse();
    filter.doFilter(two,denied,new MockFilterChain());
    assertEquals(429,denied.getStatus());
    assertEquals("60",denied.getHeader("Retry-After"));
    assertTrue(denied.getContentAsString().contains("Rate limit exceeded"));
  }
  @Test void normalEndpointsHaveIndependentCounter() throws Exception {
    RateLimitFilter filter=new RateLimitFilter(1,1);
    MockHttpServletRequest r1=new MockHttpServletRequest("GET","/api/v1/batches");
    MockHttpServletResponse a=new MockHttpServletResponse();filter.doFilter(r1,a,new MockFilterChain());
    MockHttpServletResponse b=new MockHttpServletResponse();filter.doFilter(r1,b,new MockFilterChain());
    assertEquals(200,a.getStatus());assertEquals(429,b.getStatus());
    MockHttpServletRequest r2=new MockHttpServletRequest("POST","/api/v1/auth/login");
    MockHttpServletResponse c=new MockHttpServletResponse();filter.doFilter(r2,c,new MockFilterChain());
    assertEquals(200,c.getStatus());
  }
  @Test void negativeOrZeroLimitsAreRejected() {
    assertThrows(IllegalArgumentException.class,()->new RateLimitFilter(0,1));
    assertThrows(IllegalArgumentException.class,()->new RateLimitFilter(10,-1));
  }
  @Test void healthEndpointIsNotRateLimited() throws Exception {
    RateLimitFilter filter=new RateLimitFilter(1,1);
    MockHttpServletRequest r=new MockHttpServletRequest("GET","/actuator/health");
    MockHttpServletResponse a=new MockHttpServletResponse();filter.doFilter(r,a,new MockFilterChain());
    MockHttpServletResponse b=new MockHttpServletResponse();filter.doFilter(r,b,new MockFilterChain());
    assertEquals(200,a.getStatus());assertEquals(200,b.getStatus());
  }
}
