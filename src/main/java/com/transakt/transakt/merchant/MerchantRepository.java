package com.transakt.transakt.merchant;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MerchantRepository extends JpaRepository<Merchant, String> {

    Optional<Merchant> findByEmail(String email);
    Optional<Merchant> findByApiKeyPrefix(String apiKeyPrefix);
}
