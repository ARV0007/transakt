package com.transakt.transakt.merchant;

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