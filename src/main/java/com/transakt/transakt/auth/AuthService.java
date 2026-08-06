package com.transakt.transakt.auth;

import com.transakt.transakt.common.InvalidCredentialsException;
import com.transakt.transakt.merchant.Merchant;
import com.transakt.transakt.merchant.MerchantRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final MerchantRepository merchantRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(MerchantRepository merchantRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.merchantRepository = merchantRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public LoginResponse login(LoginRequest request) {
        Merchant merchant = merchantRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Invalid email or password"));

        if (merchant.getPassword() == null
                || !passwordEncoder.matches(request.getPassword(), merchant.getPassword())) {
            throw new InvalidCredentialsException("Invalid email or password");
        }

        String token = jwtService.generateToken(
                merchant.getEmail(),
                merchant.getId(),
                merchant.getRole().name());        return new LoginResponse(token, merchant.getId(), merchant.getEmail());
    }
}
