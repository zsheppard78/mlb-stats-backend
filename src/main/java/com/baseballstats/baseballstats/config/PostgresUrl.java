package com.baseballstats.baseballstats.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns the connection string Neon (and most Postgres hosts) give you,
 * {@code postgresql://user:password@host/dbname?sslmode=require}, into what the
 * Java driver expects: a {@code jdbc:postgresql://} URL plus a separate username and password.
 * A {@code jdbc:postgresql://} URL is also accepted as-is.
 */
public record PostgresUrl(String jdbcUrl, String username, String password) {

    // Options only the C client (libpq) understands; the Java driver would ignore or warn about them.
    private static final List<String> LIBPQ_ONLY = List.of("channel_binding");

    public static PostgresUrl parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("DATABASE_URL is empty");
        }
        String value = raw.trim();

        if (value.startsWith("jdbc:postgresql://")) {
            return new PostgresUrl(value, null, null);
        }
        if (!value.startsWith("postgresql://") && !value.startsWith("postgres://")) {
            // Never echo the value: it contains the password
            throw new IllegalArgumentException(
                    "DATABASE_URL must start with postgresql://, postgres:// or jdbc:postgresql://");
        }

        URI uri;
        try {
            uri = new URI(value);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("DATABASE_URL is not a valid connection string");
        }
        if (uri.getHost() == null || uri.getRawPath() == null || uri.getRawPath().length() < 2) {
            throw new IllegalArgumentException("DATABASE_URL needs a host and a database name");
        }

        String username = null;
        String password = null;
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            int colon = userInfo.indexOf(':');
            username = decode(colon >= 0 ? userInfo.substring(0, colon) : userInfo);
            password = colon >= 0 ? decode(userInfo.substring(colon + 1)) : null;
        }

        List<String> params = new ArrayList<>();
        boolean hasSslMode = false;
        if (uri.getRawQuery() != null) {
            for (String param : uri.getRawQuery().split("&")) {
                String name = param.split("=", 2)[0];
                if (param.isBlank() || LIBPQ_ONLY.contains(name)) continue;
                if (name.equals("sslmode")) hasSslMode = true;
                params.add(param);
            }
        }
        if (!hasSslMode) params.add("sslmode=require"); // hosted databases require encryption

        int port = uri.getPort() == -1 ? 5432 : uri.getPort();
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath()
                + "?" + String.join("&", params);
        return new PostgresUrl(jdbcUrl, username, password);
    }

    private static String decode(String part) {
        return URLDecoder.decode(part, StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        // Keep the password out of logs
        return "PostgresUrl[jdbcUrl=" + jdbcUrl + ", username=" + username + ", password=****]";
    }
}
