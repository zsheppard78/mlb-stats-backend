package com.baseballstats.baseballstats.account;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Checks the "credential" the Sign in with Google button gives the site.
 * Google's tokeninfo endpoint confirms the signature; this class then checks that the token
 * was made for this site (aud), by Google (iss), hasn't expired, and has a verified email.
 */
@Component
public class GoogleTokenVerifier {

    public record GoogleUser(String subject, String email, String name) {
    }

    private static final Set<String> ISSUERS = Set.of("accounts.google.com", "https://accounts.google.com");

    private final String clientId;
    private final RestTemplate http;

    public GoogleTokenVerifier(@Value("${GOOGLE_CLIENT_ID:}") String clientId) {
        this.clientId = clientId.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        this.http = new RestTemplate(factory);
    }

    public boolean isConfigured() {
        return !clientId.isEmpty();
    }

    public String clientId() {
        return clientId;
    }

    public GoogleUser verify(String credential) {
        if (!isConfigured()) {
            throw new ApiException(503, "Google sign-in isn't set up on the server yet.");
        }
        if (credential == null || credential.isBlank() || credential.length() > 4096) {
            throw ApiException.badRequest("Missing Google sign-in details.");
        }
        Map<?, ?> claims;
        try {
            claims = http.getForObject("https://oauth2.googleapis.com/tokeninfo?id_token={token}",
                    Map.class, credential);
        } catch (RestClientException e) {
            // Google answers 400 for invalid or expired tokens
            throw ApiException.unauthorized("Google sign-in didn't work. Please try again.");
        }
        return checkClaims(claims, clientId, Instant.now());
    }

    /** The checks themselves, separate so they can be tested without calling Google. */
    static GoogleUser checkClaims(Map<?, ?> claims, String clientId, Instant now) {
        if (claims == null) throw ApiException.unauthorized("Google sign-in didn't work. Please try again.");
        String aud = str(claims.get("aud"));
        String iss = str(claims.get("iss"));
        String sub = str(claims.get("sub"));
        long exp;
        try {
            exp = Long.parseLong(str(claims.get("exp")));
        } catch (NumberFormatException e) {
            exp = 0;
        }
        if (!clientId.equals(aud) || !ISSUERS.contains(iss) || sub.isEmpty() || sub.length() > 64
                || exp <= now.getEpochSecond()) {
            throw ApiException.unauthorized("Google sign-in didn't work. Please try again.");
        }
        boolean verified = "true".equalsIgnoreCase(str(claims.get("email_verified")));
        String email = verified ? str(claims.get("email")) : "";
        String name = str(claims.get("name"));
        return new GoogleUser(sub,
                email.isEmpty() || email.length() > 254 ? null : email,
                name.isEmpty() ? null : name.substring(0, Math.min(name.length(), 100)));
    }

    private static String str(Object value) {
        return value == null ? "" : value.toString();
    }
}
