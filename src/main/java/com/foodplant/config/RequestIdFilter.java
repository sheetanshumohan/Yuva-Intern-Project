package com.foodplant.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.UUID;

@Component @Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {
  @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain)
      throws IOException,ServletException {
    String id=UUID.randomUUID().toString();
    MDC.put("requestId",id);res.setHeader("X-Request-ID",id);
    try { chain.doFilter(req,res); } finally { MDC.remove("requestId"); }
  }
}
