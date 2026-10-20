package com.baseballstats.baseballstats.account;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sign-in endpoints. A successful sign-in returns a token; the site sends it back as
 * "Authorization: Bearer <token>" on later requests.
 *
 *   GET  /api/auth/config     which sign-in options are available (and the Google client id)
 *   POST /api/auth/register   {username, password}
 *   POST /api/auth/login      {username, password}
 *   POST /api/auth/google     {credential}  (from the Sign in with Google button)
 *   POST /api/auth/logout
 *   GET  /api/me              the signed-in account and its favorite teams
 */
@RestController
public class AuthController {

    public record PasswordForm(String username, String password) {
    }

    public record GoogleForm(String credential) {
    }

    private final AccountStore store;
    private final GoogleTokenVerifier google;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    // Compared against when a username doesn't exist, so a wrong username takes as long as a wrong password
    private final String dummyHash = passwords.encode("not-a-real-password");
    private final AttemptLimiter loginLimiter = new AttemptLimiter(10, Duration.ofMinutes(15), Clock.systemUTC());
    private final AttemptLimiter signupLimiter = new AttemptLimiter(10, Duration.ofHours(1), Clock.systemUTC());

    public AuthController(AccountStore store, GoogleTokenVerifier google) {
        this.store = store;
        this.google = google;
    }

    @GetMapping("/api/auth/config")
    public Map<String, Object> config() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("passwordSignIn", true);
        body.put("googleClientId", google.isConfigured() ? google.clientId() : null);
        return body;
    }

    @PostMapping("/api/auth/register")
    public Map<String, Object> register(@RequestBody PasswordForm form, HttpServletRequest request) {
        String ip = clientIp(request);
        if (signupLimiter.isBlocked(ip)) {
            throw new ApiException(429, "Too many new accounts from here. Try again in an hour.");
        }
        String username = AccountRules.normalizeUsername(form.username());
        AccountRules.checkUsername(username);
        AccountRules.checkPassword(form.password());

        long id = store.createUsernameAccount(username, passwords.encode(form.password()));
        signupLimiter.record(ip);
        return signedIn(id);
    }

    @PostMapping("/api/auth/login")
    public Map<String, Object> login(@RequestBody PasswordForm form) {
        String username = AccountRules.normalizeUsername(form.username());
        if (username.isEmpty() || form.password() == null || form.password().isEmpty()) {
            throw ApiException.badRequest("Enter your username and password.");
        }
        if (loginLimiter.isBlocked(username)) {
            throw new ApiException(429, "Too many tries. Wait 15 minutes and try again.");
        }
        var login = store.findPasswordLogin(username);
        boolean ok = passwords.matches(form.password(), login.map(AccountStore.PasswordLogin::passwordHash).orElse(dummyHash))
                && login.isPresent();
        if (!ok) {
            loginLimiter.record(username);
            throw ApiException.unauthorized("Wrong username or password.");
        }
        loginLimiter.reset(username);
        return signedIn(login.get().id());
    }

    @PostMapping("/api/auth/google")
    public Map<String, Object> google(@RequestBody GoogleForm form) {
        GoogleTokenVerifier.GoogleUser user = google.verify(form.credential());
        return signedIn(store.findOrCreateGoogleAccount(user));
    }

    @PostMapping("/api/auth/logout")
    public Map<String, Object> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        store.deleteSession(Tokens.fromHeader(authorization));
        return Map.of("signedOut", true);
    }

    @GetMapping("/api/me")
    public Map<String, Object> me(@RequestHeader(value = "Authorization", required = false) String authorization) {
        AccountStore.Account account = requireAccount(store, authorization);
        return accountBody(account, store.favorites(account.id()));
    }

    // ---------------------------------------------------------------- helpers

    private Map<String, Object> signedIn(long userId) {
        AccountStore.Account account = store.findAccount(userId)
                .orElseThrow(() -> new IllegalStateException("Account " + userId + " disappeared"));
        Map<String, Object> body = accountBody(account, store.favorites(userId));
        body.put("token", store.createSession(userId));
        return body;
    }

    static Map<String, Object> accountBody(AccountStore.Account account, List<Integer> favorites) {
        Map<String, Object> user = new LinkedHashMap<>();
        user.put("id", account.id());
        user.put("username", account.username());
        user.put("displayName", account.displayName() != null ? account.displayName()
                : account.username() != null ? account.username() : "Google user");
        user.put("google", account.google());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", user);
        body.put("favorites", favorites);
        return body;
    }

    static AccountStore.Account requireAccount(AccountStore store, String authorization) {
        return store.accountForToken(Tokens.fromHeader(authorization))
                .orElseThrow(() -> ApiException.unauthorized("Please sign in again."));
    }

    /** Render puts the visitor's address first in X-Forwarded-For. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) return forwarded.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
