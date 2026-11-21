package com.foodplant.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Configuration @EnableMethodSecurity
@OpenAPIDefinition(info=@Info(title="Food Plant Traceability & Inventory API",version="1.0.0",
 description="Kochi spice processing batch traceability, production lines, suppliers and stock movement"))
@SecurityScheme(name="bearerAuth",type=SecuritySchemeType.HTTP,bearerFormat="JWT",scheme="bearer")
public class SecurityConfig {
  @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
  @Bean SecretKey jwtKey(@Value("${app.jwt.secret}") String secret) {
    // Refuse to launch without a real configured key: no dangerous default production secret.
    if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32)
      throw new IllegalStateException("JWT_SECRET must be set and contain at least 32 UTF-8 bytes");
    return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
  }
  @Bean JwtEncoder jwtEncoder(SecretKey key) { return new NimbusJwtEncoder(new ImmutableSecret<>(key)); }
  @Bean JwtDecoder jwtDecoder(SecretKey key) {
    NimbusJwtDecoder decoder=NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("food-plant-api"));
    return decoder;
  }
  @Bean SecurityFilterChain apiSecurity(HttpSecurity http) throws Exception {
    JwtAuthenticationConverter converter=new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
      String role=jwt.getClaimAsString("role");
      return role==null?List.of():List.of(new SimpleGrantedAuthority("ROLE_"+role));
    });
    http.csrf(csrf->csrf.disable())
      .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
      .authorizeHttpRequests(a->a
        .requestMatchers("/actuator/health","/api/v1/auth/login","/swagger-ui/**","/swagger-ui.html","/v3/api-docs/**").permitAll()
        .anyRequest().authenticated())
      .oauth2ResourceServer(o->o.jwt(j->j.jwtAuthenticationConverter(converter)))
      .exceptionHandling(e->e
        .authenticationEntryPoint((req,res,ex)->{
          res.setStatus(HttpStatus.UNAUTHORIZED.value());res.setContentType("application/json");
          res.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Authentication required\"}");
        })
        .accessDeniedHandler((req,res,ex)->{
          res.setStatus(HttpStatus.FORBIDDEN.value());res.setContentType("application/json");
          res.getWriter().write("{\"status\":403,\"error\":\"Forbidden\",\"message\":\"Insufficient permissions\"}");
        }));
    return http.build();
  }
}
