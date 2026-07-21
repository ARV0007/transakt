package com.transakt.transakt.merchant;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Service
public class MerchantService {

    private final Map<String, Merchant> store = new HashMap<>();

    public Merchant create(Merchant merchant) {
        merchant.setId(UUID.randomUUID().toString());
        merchant.setCreatedAt(Instant.now());
        store.put(merchant.getId(), merchant);
        return merchant;
    }

    public Merchant getById(String id) {
        return store.get(id);
    }
    public List<Merchant> getAll() {
        return new ArrayList<>(store.values());
    }

    public Merchant update(String id, Merchant updated) {
        Merchant existing = store.get(id);
        if (existing == null) {
            return null;
        }
        existing.setName(updated.getName());
        existing.setEmail(updated.getEmail());
        existing.setBusinessName(updated.getBusinessName());
        return existing;
    }

    public boolean delete(String id) {
        return store.remove(id) != null;
    }

}
