package com.baseballstats.baseballstats.account;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

/** Rules for usernames and passwords, with messages people can act on. */
public final class AccountRules {

    private static final Pattern USERNAME = Pattern.compile("[a-z0-9_]{3,20}");
    public static final int MIN_PASSWORD = 8;
    // bcrypt only looks at the first 72 bytes of a password
    public static final int MAX_PASSWORD_BYTES = 72;

    private AccountRules() {
    }

    /** Lower-cases and trims, so sign-in isn't case-sensitive. */
    public static String normalizeUsername(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    public static void checkUsername(String username) {
        if (!USERNAME.matcher(username).matches()) {
            throw ApiException.badRequest(
                    "Usernames are 3–20 characters: letters, numbers and underscores.");
        }
    }

    public static void checkPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD) {
            throw ApiException.badRequest("Passwords need at least " + MIN_PASSWORD + " characters.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > MAX_PASSWORD_BYTES) {
            throw ApiException.badRequest("Passwords can be at most " + MAX_PASSWORD_BYTES + " characters.");
        }
    }

    /** MLB team ids are small positive numbers (108–158 today). */
    public static void checkTeamId(int teamId) {
        if (teamId <= 0 || teamId > 9999) {
            throw ApiException.badRequest("That isn't a valid team.");
        }
    }
}
