package com.example.troubleticket.repository;

import com.example.troubleticket.entity.UserAccount;
import org.springframework.data.repository.Repository;

import java.util.Optional;

public interface UserAccountRepository extends Repository<UserAccount, String> {
    Optional<UserAccount> findByUsername(String username);
}
