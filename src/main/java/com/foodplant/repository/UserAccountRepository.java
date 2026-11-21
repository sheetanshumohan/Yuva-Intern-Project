package com.foodplant.repository;
import com.foodplant.entity.UserAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface UserAccountRepository extends JpaRepository<UserAccount,UUID> { Optional<UserAccount> findByEmailIgnoreCase(String email); }
