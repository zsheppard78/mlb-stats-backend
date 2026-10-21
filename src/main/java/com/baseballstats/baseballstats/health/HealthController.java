package com.baseballstats.baseballstats.health;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GET /api/health: checks that the backend is up and can reach its database.
 * Example: {"status":"ok","database":"PostgreSQL","tablesReady":true}
 */
@RestController
public class HealthController {

    private final JdbcTemplate jdbc;

    public HealthController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * GET /api/ping: answers instantly without touching the database.
     * The site calls it to wake the backend up, and an uptime monitor can call it to keep the
     * backend awake, without waking Neon (which would use up its free compute hours).
     */
    @GetMapping("/api/ping")
    public Map<String, Object> ping() {
        return Map.of("status", "ok");
    }

    @GetMapping("/api/health")
    public Map<String, Object> health() {
        Map<String, Object> body = new LinkedHashMap<>();
        try {
            String product = jdbc.execute((ConnectionCallback<String>) connection ->
                    connection.getMetaData().getDatabaseProductName());
            Integer tables = jdbc.queryForObject(
                    "select count(*) from information_schema.tables "
                            + "where lower(table_name) in ('app_user', 'favorite_team')",
                    Integer.class);
            body.put("status", "ok");
            body.put("database", product);
            body.put("tablesReady", tables != null && tables == 2);
        } catch (DataAccessException e) {
            body.put("status", "degraded");
            body.put("database", "unreachable");
        }
        return body;
    }
}
