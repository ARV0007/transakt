package com.transakt.transakt.auth;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class LoginResponse {

    private String token;
    private String merchantId;
    private String email;
}
