package com.baseballstats.baseballstats.account;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/** Every database query for accounts, sign-in sessions and favorite teams. */
@Repository
public class AccountStore {

    /** What the site sees about a signed-in person. */
    public record Account(long id, String username, String displayName, boolean google) {
    }

    /** A username account plus its password hash, for checking a sign-in. */
    public record PasswordLogin(long id, String passwordHash) {
    }

    public static final Duration SESSION_LENGTH = Duration.ofDays(30);
    public static final int MAX_FAVORITES = 30;

    private static final RowMapper<Account> ACCOUNT = (rs, i) -> new Account(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("display_name"),
            rs.getString("google_subject") != null);

    private final JdbcTemplate jdbc;

    public AccountStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static OffsetDateTime now() {
        return OffsetDateTime.now(ZoneOffset.UTC);
    }

    // ---------------------------------------------------------------- accounts

    public Optional<PasswordLogin> findPasswordLogin(String username) {
        return jdbc.query("select id, password_hash from app_user where username = ? and password_hash is not null",
                (rs, i) -> new PasswordLogin(rs.getLong("id"), rs.getString("password_hash")),
                username).stream().findFirst();
    }

    public Optional<Account> findAccount(long id) {
        return jdbc.query("select id, username, display_name, google_subject from app_user where id = ?",
                ACCOUNT, id).stream().findFirst();
    }

    /** Creates a username account; fails with 409 if the username is taken. */
    public long createUsernameAccount(String username, String passwordHash) {
        try {
            return insertUser("insert into app_user (username, password_hash, display_name) values (?, ?, ?)",
                    username, passwordHash, username);
        } catch (DuplicateKeyException e) {
            throw new ApiException(409, "That username is taken. Try another one.");
        }
    }

    /** Finds the account for this Google login, creating it the first time. */
    public long findOrCreateGoogleAccount(GoogleTokenVerifier.GoogleUser google) {
        List<Long> existing = jdbc.queryForList("select id from app_user where google_subject = ?",
                Long.class, google.subject());
        if (!existing.isEmpty()) return existing.get(0);
        try {
            return insertUser("insert into app_user (google_subject, email, display_name) values (?, ?, ?)",
                    google.subject(), google.email(), google.name());
        } catch (DuplicateKeyException e) {
            // Signed in twice at the same moment, or the email belongs to an older Google account
            existing = jdbc.queryForList("select id from app_user where google_subject = ?",
                    Long.class, google.subject());
            if (!existing.isEmpty()) return existing.get(0);
            return insertUser("insert into app_user (google_subject, email, display_name) values (?, ?, ?)",
                    google.subject(), null, google.name());
        }
    }

    private long insertUser(String sql, Object... args) {
        KeyHolder keys = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"id"});
            for (int i = 0; i < args.length; i++) ps.setObject(i + 1, args[i]);
            return ps;
        }, keys);
        Number id = keys.getKey();
        if (id == null) throw new IllegalStateException("The database didn't return the new account's id");
        return id.longValue();
    }

    // ---------------------------------------------------------------- sessions

    /** Starts a session and returns the token the browser keeps. */
    public String createSession(long userId) {
        jdbc.update("delete from user_session where expires_at < ?", now()); // tidy up old sessions
        String token = Tokens.newToken();
        jdbc.update("insert into user_session (token_hash, user_id, expires_at) values (?, ?, ?)",
                Tokens.hash(token), userId, now().plus(SESSION_LENGTH));
        return token;
    }

    public Optional<Account> accountForToken(String token) {
        if (token == null) return Optional.empty();
        return jdbc.query("select u.id, u.username, u.display_name, u.google_subject "
                        + "from user_session s join app_user u on u.id = s.user_id "
                        + "where s.token_hash = ? and s.expires_at > ?",
                ACCOUNT, Tokens.hash(token), now()).stream().findFirst();
    }

    public void deleteSession(String token) {
        if (token != null) jdbc.update("delete from user_session where token_hash = ?", Tokens.hash(token));
    }

    // ---------------------------------------------------------------- favorites

    public List<Integer> favorites(long userId) {
        return jdbc.queryForList("select team_id from favorite_team where user_id = ? order by created_at, team_id",
                Integer.class, userId);
    }

    public void addFavorite(long userId, int teamId) {
        Integer count = jdbc.queryForObject("select count(*) from favorite_team where user_id = ?",
                Integer.class, userId);
        if (count != null && count >= MAX_FAVORITES) {
            throw ApiException.badRequest("You can have up to " + MAX_FAVORITES + " favorite teams.");
        }
        jdbc.update("insert into favorite_team (user_id, team_id, created_at) select ?, ?, ? "
                        + "where not exists (select 1 from favorite_team where user_id = ? and team_id = ?)",
                userId, teamId, now(), userId, teamId);
    }

    public void removeFavorite(long userId, int teamId) {
        jdbc.update("delete from favorite_team where user_id = ? and team_id = ?", userId, teamId);
    }

    /** Replaces the whole list, keeping its order. */
    @Transactional
    public void replaceFavorites(long userId, List<Integer> teamIds) {
        jdbc.update("delete from favorite_team where user_id = ?", userId);
        OffsetDateTime start = now();
        for (int i = 0; i < teamIds.size(); i++) {
            jdbc.update("insert into favorite_team (user_id, team_id, created_at) values (?, ?, ?)",
                    userId, teamIds.get(i), start.plusNanos(i * 1_000_000L));
        }
    }
}
