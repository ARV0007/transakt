package com.transakt.transakt.merchant;

import com.transakt.transakt.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class MerchantService {

    private final MerchantRepository merchantRepository;

    public MerchantService(MerchantRepository merchantRepository) {
        this.merchantRepository = merchantRepository;
    }

    public Merchant create(Merchant merchant) {
        merchant.setId(UUID.randomUUID().toString());
        merchant.setCreatedAt(Instant.now());
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