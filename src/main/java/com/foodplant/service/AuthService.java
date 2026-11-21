package com.foodplant.service;

import com.foodplant.dto.ApiDtos.*;
import com.foodplant.entity.*;
import com.foodplant.repository.UserAccountRepository;
import com.foodplant.exception.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service
public class AuthService {
  private final UserAccountRepository users;
  private final PasswordEncoder passwordEncoder;
  private final JwtEncoder encoder;
  private final long ttlMinutes;
  public AuthService(UserAccountRepository users,PasswordEncoder encoderPassword,JwtEncoder encoder,
                     @Value("${app.jwt.ttl-minutes}") long ttlMinutes) {
    this.users=users;this.passwordEncoder=encoderPassword;this.encoder=encoder;this.ttlMinutes=ttlMinutes;
  }
  @Transactional(readOnly=true)
  public TokenResponse login(LoginRequest request) {
    UserAccount user=users.findByEmailIgnoreCase(request.email()).orElse(null);
    if(user==null || !user.isEnabled() || !passwordEncoder.matches(request.password(),user.getPasswordHash()))
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Invalid credentials");
    Instant now=Instant.now();
    JwtClaimsSet claims=JwtClaimsSet.builder().issuer("food-plant-api").subject(user.getId().toString())
        .issuedAt(now).expiresAt(now.plusSeconds(ttlMinutes*60))
        .claim("role",user.getRole().name()).claim("email",user.getEmail()).build();
    String jwt=encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    return new TokenResponse(jwt,"Bearer",ttlMinutes*60,user.getRole().name());
  }
  @Transactional
  public UserResponse createUser(UserRequest request) {
    String normalized=request.email().trim().toLowerCase(Locale.ROOT);
    if(users.findByEmailIgnoreCase(normalized).isPresent()) throw new ConflictException("Email already exists");
    UserAccount u=new UserAccount();u.setEmail(normalized);
    u.setPasswordHash(passwordEncoder.encode(request.password()));u.setRole(request.role());u.setEnabled(true);
    return map(users.save(u));
  }
  @Transactional(readOnly=true)
  public List<UserResponse> listUsers() { return users.findAll().stream().map(AuthService::map).toList(); }
  public static UserResponse map(UserAccount u) { return new UserResponse(u.getId(),u.getEmail(),u.getRole(),u.isEnabled()); }
}
