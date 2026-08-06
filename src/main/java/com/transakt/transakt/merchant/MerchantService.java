package com.transakt.transakt.merchant;

import com.transakt.transakt.common.ResourceNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;   // ← NEW (1)
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;                     // ← NEW (2)

    public MerchantService(MerchantRepository merchantRepository,
                           PasswordEncoder passwordEncoder) {          // ← NEW (3)
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;                        // ← NEW (3)
    }

    public Merchant create(Merchant merchant) {
        merchant.setId(UUID.randomUUID().toString());
        merchant.setApiKey("tk_" + UUID.randomUUID().toString().replace("-", ""));
        merchant.setCreatedAt(Instant.now());

        if (merchant.getPassword() != null) {                          // ← NEW (4)
            merchant.setPassword(passwordEncoder.encode(merchant.getPassword()));
        }

        return merchantRepository.save(merchant);
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