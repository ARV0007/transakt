package com.transakt.transakt.merchant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * The body of POST /api/v1/merchants.
 *
 * There is no role field here, and that is the whole reason this class exists.
 * Signup binds a request body from an unauthenticated caller - it has to be open,
 * or nobody could create the first account - and it used to bind that body straight
 * onto the Merchant ENTITY, which has a role column. Merchant.role has a field
 * initialiser of MERCHANT, but a field initialiser runs at CONSTRUCTION and
 * Jackson's setter runs after it, so {"role":"ADMIN"} in the body overwrote the
 * default before the service ever saw the object, and nothing reset it.
 *
 * With no field to bind to, a forged role evaporates during deserialisation. The
 * attack is not rejected, it cannot be expressed - the same reasoning that removed
 * merchantId from CreatePaymentRequest.
 */
@Data
public class CreateMerchantRequest {

    @NotBlank(message = "name is required")
    @Size(max = 255, message = "name must be at most 255 characters")
    private String name;

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid address")
    @Size(max = 255, message = "email must be at most 255 characters")
    private String email;

    /**
     * @NotBlank closes a long-standing gap: signup used to accept a merchant with no
     * password at all, creating an account that could never log in through the human
     * door and could only ever be reached with its API key.
     *
     * 72 is not arbitrary. BCrypt silently truncates its input at 72 bytes, so a
     * longer password would have characters that look like they count and do not.
     */
    @NotBlank(message = "password is required")
    @Size(min = 6, max = 72, message = "password must be between 6 and 72 characters")
    private String password;

    @Size(max = 255, message = "businessName must be at most 255 characters")
    private String businessName;
}
