package com.foodplant.controller;

import com.foodplant.dto.ApiDtos.*;
import com.foodplant.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/v1/auth")
public class AuthController {
  private final AuthService service;
  public AuthController(AuthService service) { this.service=service; }
  @PostMapping("/login") public TokenResponse login(@Valid @RequestBody LoginRequest request) { return service.login(request); }
  @SecurityRequirement(name="bearerAuth") @PreAuthorize("hasRole('ADMIN')")
  @PostMapping("/users") public ResponseEntity<UserResponse> create(@Valid @RequestBody UserRequest req) {
    return ResponseEntity.status(201).body(service.createUser(req));
  }
  @SecurityRequirement(name="bearerAuth") @PreAuthorize("hasRole('ADMIN')")
  @GetMapping("/users") public List<UserResponse> list() { return service.listUsers(); }
}
