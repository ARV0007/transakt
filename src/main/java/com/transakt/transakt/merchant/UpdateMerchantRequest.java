package com.transakt.transakt.merchant;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * The body of PUT /api/v1/merchants/{id}.
 *
 * The last controller in the project that bound @RequestBody to an ENTITY, and the
 * reason this class exists is the rule rather than a specific exploit. This route is
 * ADMIN-only, so a role in the body was never an escalation — an administrator can
 * already do anything. But it is the same SHAPE as the signup bug: an untrusted body
 * bound onto a class with columns the caller must not control, relying on the service
 * to ignore the ones that matter.
 *
 * "No controller binds a request body to an entity" is a rule you can check by
 * reading, in one pass, forever. "The service ignores the dangerous fields" is a
 * rule you have to re-verify every time anyone touches it. The first kind is worth
 * having; the second is how signup stayed broken for twenty-two days.
 *
 * PUT is a full replacement, so name and email are required: omitting one means
 * "set it to nothing", and these columns are NOT NULL. businessName is genuinely
 * optional and clears when omitted, which is what PUT means.
 */
@Data
public class UpdateMerchantRequest {

    @NotBlank(message = "name is required")
    @Size(max = 255, message = "name must be at most 255 characters")
    private String name;

    @NotBlank(message = "email is required")
    @Email(message = "email must be a valid address")
    @Size(max = 255, message = "email must be at most 255 characters")
    private String email;

    @Size(max = 255, message = "businessName must be at most 255 characters")
    private String businessName;
}
