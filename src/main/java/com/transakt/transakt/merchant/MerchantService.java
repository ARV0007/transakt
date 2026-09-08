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

    /**
     * SSRF WARNING - known, deliberate, and not yet closed.
     *
     * A webhook URL is a URL THIS SERVER makes outbound requests to, using this
     * server's network identity. The pattern on UpdateWebhookRequest does not make
     * that safe: https://169.254.169.254/ is a perfectly well-formed URL and is the
     * cloud instance-metadata endpoint. So is http://localhost:5432, and so is any
     * address on the private network. Even with the response body discarded, the
     * status code and the timing map the internal network for whoever asked. Signup
     * is permitAll, so anyone on the internet can register and set one.
     *
     * The real fix belongs at DELIVERY time, in WebhookConsumer: resolve the host and
     * refuse loopback, link-local and site-local addresses. It cannot live here,
     * because a hostname that resolves to a public address when it is saved can
     * resolve to 127.0.0.1 when it is called. That is DNS rebinding: validating the
     * string is not validating the destination.
     *
     * Not currently exploitable - no broker is deployed, so WebhookConsumer never
     * runs and nothing outbound is ever sent. This must be closed BEFORE Kafka is
     * deployed, not after.
     */
    public Merchant updateWebhookUrl(String merchantId, String webhookUrl) {
        Merchant merchant = getById(merchantId);
        merchant.setWebhookUrl(webhookUrl);

        // save() merges, and apiKey is @Transient, so the merchant that comes back
        // carries a null apiKey. That is correct: the key is shown exactly once, at
        // signup, and there is deliberately no way to read it again.
        return merchantRepository.save(merchant);
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