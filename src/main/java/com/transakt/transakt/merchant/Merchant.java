package com.transakt.transakt.merchant;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "merchants")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Merchant {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "business_name")
    private String businessName;

    @Column(name = "api_key", unique = true)
    @Transient
    private String apiKey;

    @Column(name = "api_key_prefix", length = 16)
    private String apiKeyPrefix;

    @JsonIgnore
    @Column(name = "api_key_hash", length = 64)
    private String apiKeyHash;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MerchantRole role = MerchantRole.MERCHANT;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;
}

