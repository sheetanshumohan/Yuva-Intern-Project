package com.foodplant.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.time.Duration;

/** Per-instance fixed-window IP throttle. For multi-replica production, replace with Redis/edge policy. */
@Component @Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {
  private final Cache<String,Window> windows=Caffeine.newBuilder().maximumSize(100_000)
      .expireAfterAccess(Duration.ofMinutes(3)).build();
  private final int generalLimit, loginLimit;
  public RateLimitFilter(@Value("${app.rate-limit.per-minute}") int generalLimit,
                         @Value("${app.rate-limit.login-per-minute}") int loginLimit) {
    if(generalLimit<1 || loginLimit<1)
      throw new IllegalArgumentException("Rate limits must be positive");
    this.generalLimit=generalLimit; this.loginLimit=loginLimit;
  }
  private static final class Window {
    long since;int used;
    Window(long since) { this.since=since; }
    synchronized boolean acquire(int limit,long now) {
      if(now-since>=60_000) { since=now;used=0; }
      if(used>=limit) return false;
      used++;return true;
    }
  }
  @Override protected boolean shouldNotFilter(HttpServletRequest request) {
    return !request.getRequestURI().startsWith("/api/v1/");
  }
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
      throws ServletException,IOException {
    boolean isLogin=req.getRequestURI().equals("/api/v1/auth/login");
    String key=(isLogin?"login:":"general:")+req.getRemoteAddr();
    int limit=isLogin?loginLimit:generalLimit;
    long now=System.currentTimeMillis();
    Window window=windows.get(key,k->new Window(now));
    if(window==null || !window.acquire(limit,now)) {
      res.setStatus(429);res.setContentType("application/json");res.setHeader("Retry-After","60");
      res.getWriter().write("{\"status\":429,\"error\":\"Too Many Requests\",\"message\":\"Rate limit exceeded\"}");
      return;
    }
    chain.doFilter(req,res);
  }
}
