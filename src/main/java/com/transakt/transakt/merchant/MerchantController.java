package com.transakt.transakt.merchant;

import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping
    public Merchant create(@RequestBody Merchant merchant) {
        return merchantService.create(merchant);
    }

    /**
     * "me" is not a path variable, and that is the entire design.
     *
     * There is no id in this route for a client to change, so "merchant A edits
     * merchant B" is not a request that can be expressed - the same reasoning that
     * removed merchantId from CreatePaymentRequest on Day 9. The identity comes from
     * the credential, which both auth filters resolve to the merchant id.
     */
    @PatchMapping("/me")
    public Merchant updateMyWebhook(Authentication authentication,
                                    @Valid @RequestBody UpdateWebhookRequest request) {
        return merchantService.updateWebhookUrl(authentication.getName(), request.getWebhookUrl());
    }

    @GetMapping("/{id}")
    public Merchant getById(@PathVariable String id) {
        return merchantService.getById(id);
    }
    @GetMapping
    public List<Merchant> getAll() {
        return merchantService.getAll();
    }

    @PutMapping("/{id}")
    public Merchant update(@PathVariable String id, @RequestBody Merchant merchant) {
        return merchantService.update(id, merchant);
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable String id) {
        return merchantService.delete(id);
    }
}