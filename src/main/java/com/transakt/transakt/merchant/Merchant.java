package com.transakt.transakt.merchant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Merchant {
    private String id;
    private String name;
    private String email;
    private String businessName;
    private Instant createdAt;
}

