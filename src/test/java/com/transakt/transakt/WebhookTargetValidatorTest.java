package com.transakt.transakt;

import com.transakt.transakt.webhook.WebhookTargetValidator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The SSRF guard.
 *
 * A unit test, and every case uses a LITERAL IP address on purpose:
 * InetAddress.getAllByName returns immediately for a literal and performs no DNS
 * lookup at all. So this test is deterministic, needs no network, and cannot go
 * flaky because someone's resolver was slow.
 */
class WebhookTargetValidatorTest {

    private final WebhookTargetValidator validator = new WebhookTargetValidator(false);

    /**
     * The one that matters. 169.254.169.254 is the cloud instance-metadata endpoint
     * on AWS, GCP and Azure. Before this guard existed, PATCH /api/v1/merchants/me
     * accepted this URL with a 200 and the consumer would have fetched it.
     */
    @Test
    void refusesTheCloudMetadataEndpoint() {
        assertThat(validator.isAllowed("http://169.254.169.254/latest/meta-data/")).isFalse();
    }

    @Test
    void refusesLoopback() {
        assertThat(validator.isAllowed("http://127.0.0.1/hook")).isFalse();
        assertThat(validator.isAllowed("http://127.0.0.1:5432/hook")).isFalse();
    }

    @Test
    void refusesIpv6Loopback() {
        assertThat(validator.isAllowed("http://[::1]/hook")).isFalse();
    }

    @Test
    void refusesPrivateRanges() {
        assertThat(validator.isAllowed("http://10.0.0.5/hook")).isFalse();
        assertThat(validator.isAllowed("http://172.16.0.1/hook")).isFalse();
        assertThat(validator.isAllowed("http://192.168.1.1/hook")).isFalse();
    }

    @Test
    void refusesTheWildcardAddress() {
        assertThat(validator.isAllowed("http://0.0.0.0/hook")).isFalse();
    }

    @Test
    void refusesAMalformedUrl() {
        assertThat(validator.isAllowed("not a url at all")).isFalse();
        assertThat(validator.isAllowed("https://")).isFalse();
    }

    /**
     * Fails closed. If we cannot work out where a URL points, we do not send to it -
     * the opposite choice would make every typo an outbound request to whatever the
     * resolver eventually decided.
     */
    @Test
    void refusesAHostThatDoesNotResolve() {
        assertThat(validator.isAllowed("https://nx.invalid/hook")).isFalse();
    }

    @Test
    void allowsAPublicAddress() {
        assertThat(validator.isAllowed("https://1.1.1.1/hooks/transakt")).isTrue();
    }

    /**
     * The escape hatch that makes local development and the existing consumer tests
     * possible. It defaults to false, so a deploy that forgets the variable blocks
     * private targets rather than allowing them.
     */
    @Test
    void allowsEverythingWhenPrivateTargetsAreExplicitlyPermitted() {
        WebhookTargetValidator permissive = new WebhookTargetValidator(true);

        assertThat(permissive.isAllowed("http://127.0.0.1/hook")).isTrue();
        assertThat(permissive.isAllowed("http://169.254.169.254/latest/meta-data/")).isTrue();
    }
}
