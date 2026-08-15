package com.transakt.transakt.merchant;

import com.transakt.transakt.common.ApiKeyHasher;
import com.transakt.transakt.common.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;

    public MerchantService(MerchantRepository merchantRepository,
                           PasswordEncoder passwordEncoder) {
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Merchant create(Merchant merchant) {
        merchant.setId(UUID.randomUUID().toString());

        String apiKey = "tk_" + UUID.randomUUID().toString().replace("-", "");
        merchant.setApiKey(apiKey);
        merchant.setApiKeyPrefix(ApiKeyHasher.prefixOf(apiKey));
        merchant.setApiKeyHash(ApiKeyHasher.hash(apiKey));

        merchant.setCreatedAt(Instant.now());

        if (merchant.getPassword() != null) {
            merchant.setPassword(passwordEncoder.encode(merchant.getPassword()));
        }

        // The ID is already assigned, so Spring Data's isNew() is false and save()
        // calls merge(), which returns a NEW managed instance carrying only the
        // persistent state. apiKey is @Transient, so it doesn't survive that copy —
        // this is the merchant's only chance to ever see it.
        Merchant saved = merchantRepository.save(merchant);
        saved.setApiKey(apiKey);
        return saved;
    }

    public Merchant getById(String id) {
        return merchantRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Merchant not found: " + id));
    }

    public List<Merchant> getAll() {
        return merchantRepository.findAll();
    }

    public Merchant update(String id, Merchant updated) {
        Merchant existing = merchantRepository.findById(id).orElse(null);
        if (existing == null) {
            return null;
        }
        existing.setName(updated.getName());
        existing.setEmail(updated.getEmail());
        existing.setBusinessName(updated.getBusinessName());
        return merchantRepository.save(existing);
    }

    public boolean delete(String id) {
        if (!merchantRepository.existsById(id)) {
            return false;
        }
        merchantRepository.deleteById(id);
        return true;
    }
}