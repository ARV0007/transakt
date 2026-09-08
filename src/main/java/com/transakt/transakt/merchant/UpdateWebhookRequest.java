package com.transakt.transakt.merchant;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * The body of PATCH /api/v1/merchants/me.
 *
 * One field, deliberately nullable: sending null clears the webhook, so a merchant
 * can turn delivery off without a second endpoint.
 *
 * That does leave the classic PATCH ambiguity - to Jackson, "omitted" and
 * "explicitly null" are the same thing, so this body cannot say "leave the URL alone
 * while changing something else". With a single field there is nothing else to
 * change, so it costs nothing today. The moment a second field arrives, this DTO
 * needs a wrapper type (Optional or JsonNullable) to tell the two apart.
 *
 * The pattern is FORMAT validation, not safety. See MerchantService.updateWebhookUrl
 * for why a perfectly well-formed URL can still be a hostile destination.
 */
@Data
public class UpdateWebhookRequest {

    @Size(max = 512, message = "webhookUrl must be at most 512 characters")
    @Pattern(regexp = "^https?://\\S+$",
            message = "webhookUrl must start with http:// or https:// and contain no spaces")
    private String webhookUrl;
}
