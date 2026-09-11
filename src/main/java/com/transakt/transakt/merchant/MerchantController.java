package com.transakt.transakt.merchant;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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

    /**
     * Signup. permitAll, because otherwise nobody could create the first account -
     * which makes this body the largest attack surface in the API, and why it binds
     * to a DTO rather than to the entity. See CreateMerchantRequest.
     */
    @PostMapping
    public Merchant create(@Valid @RequestBody CreateMerchantRequest request) {
        return merchantService.create(request);
    }

    /**
     * "me" is not a path variable, and that is the entire design.
     *
     * There is no id in this route for a client to change, so "merchant A edits
     * merchant B" is not a request that can be expressed - the same reasoning that
     * removed merchantId from CreatePaymentRequest on Day 9. The identity comes from
     * the credential, which both auth filters resolve to the merchant id.
     */
    /**
     * Reads the caller's own record. Closes the asymmetry left by the PATCH below,
     * which let a merchant WRITE its webhook URL with no way to read it back.
     *
     * Note that @GetMapping("/{id}") below also matches this URL. Spring MVC picks
     * this one because it resolves by pattern SPECIFICITY - a literal segment beats
     * a template variable - which is a different rule from Spring Security's
     * first-match-wins. Two matching systems, two rules, same application.
     */
    @GetMapping("/me")
    public Merchant getMe(Authentication authentication) {
        return merchantService.getById(authentication.getName());
    }

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

    /**
     * ADMIN-only. Binds to a DTO like every other write path — see
     * UpdateMerchantRequest for why that matters even on a route where a forged
     * role would change nothing.
     */
    @PutMapping("/{id}")
    public Merchant update(@PathVariable String id, @Valid @RequestBody UpdateMerchantRequest request) {
        return merchantService.update(id, request);
    }

    /**
     * 204 No Content: the delete succeeded and there is nothing to return. An id
     * that does not exist throws from the service and becomes a 404.
     *
     * This used to return a boolean, which meant a missing merchant answered 200
     * with the body `false` — indistinguishable from success to any client that
     * reads the status code.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String id) {
        merchantService.delete(id);
    }
}