package com.transakt.transakt.webhook;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;

/**
 * Decides whether this server is willing to make an outbound request to a URL a
 * merchant chose.
 *
 * WHY THIS EXISTS. A webhook URL is the one field in the API where a merchant
 * supplies a destination that WE then connect to, using OUR network identity.
 * Signup is open, so anyone on the internet can register and set one. The format
 * validation on UpdateWebhookRequest does not help: https://169.254.169.254/ is a
 * perfectly well-formed URL and is the cloud instance-metadata endpoint. So is
 * http://localhost:5432, and so is every address on the private network. Even with
 * the response body discarded, the status code and the timing map an internal
 * network for whoever asked. That is server-side request forgery.
 *
 * WHY THE CHECK IS HERE AND NOT IN VALIDATION. Resolving the hostname when the URL
 * is SAVED does not work. A name that resolves to a public address at save time can
 * resolve to 127.0.0.1 at call time, because whoever registered it controls the
 * record and its TTL. That is DNS rebinding: nothing about the string changed, the
 * destination did. The only check that means anything is the one performed against
 * the address you are about to connect to, immediately before connecting.
 *
 * WHY IT FAILS CLOSED. An unresolvable host is refused rather than attempted. If we
 * cannot determine where a URL points, we do not send anything there.
 */
@Component
public class WebhookTargetValidator {

    /**
     * Local development and the test suite need to reach localhost and hostnames
     * that do not resolve. Production must never set this. The default is false so
     * that a forgotten environment variable fails SAFE - a deploy that misses this
     * setting blocks private targets rather than allowing them.
     */
    private final boolean allowPrivateTargets;

    public WebhookTargetValidator(
            @Value("${webhook.allow-private-targets:false}") boolean allowPrivateTargets) {
        this.allowPrivateTargets = allowPrivateTargets;
    }

    public boolean isAllowed(String url) {
        if (allowPrivateTargets) {
            return true;
        }

        String host;
        try {
            host = URI.create(url).getHost();
        } catch (IllegalArgumentException e) {
            return false;
        }
        if (host == null || host.isBlank()) {
            return false;
        }

        // URI.getHost() keeps the brackets on a literal IPv6 host.
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }

        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            return false;
        }

        // EVERY address a name resolves to must be acceptable. A hostname with one
        // public A record and one pointing at 127.0.0.1 is an attack, not a typo.
        for (InetAddress address : addresses) {
            if (isPrivate(address)) {
                return false;
            }
        }
        return true;
    }

    private boolean isPrivate(InetAddress address) {
        return address.isLoopbackAddress()          // 127.0.0.0/8 and ::1
                || address.isLinkLocalAddress()     // 169.254.0.0/16 - cloud metadata lives here
                || address.isSiteLocalAddress()     // 10/8, 172.16/12, 192.168/16
                || address.isAnyLocalAddress()      // 0.0.0.0
                || address.isMulticastAddress()
                || isUniqueLocalIpv6(address);
    }

    /**
     * fc00::/7, the IPv6 equivalent of the private ranges. Java's
     * isSiteLocalAddress() only covers the DEPRECATED fec0::/10 for IPv6, so this
     * range would otherwise pass every check above.
     */
    private boolean isUniqueLocalIpv6(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xFE) == 0xFC;
    }
}
