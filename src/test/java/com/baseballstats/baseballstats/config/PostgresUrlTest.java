package com.baseballstats.baseballstats.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PostgresUrlTest {

    @Test
    void convertsNeonConnectionString() {
        PostgresUrl url = PostgresUrl.parse(
                "postgresql://app_owner:npg_Secret123@ep-cool-sun-a5b6.us-east-2.aws.neon.tech/neondb?sslmode=require&channel_binding=require");
        assertEquals("jdbc:postgresql://ep-cool-sun-a5b6.us-east-2.aws.neon.tech:5432/neondb?sslmode=require", url.jdbcUrl());
        assertEquals("app_owner", url.username());
        assertEquals("npg_Secret123", url.password());
    }

    @Test
    void addsSslAndKeepsCustomPort() {
        PostgresUrl url = PostgresUrl.parse("postgres://u:p@db.example.com:6543/stats");
        assertEquals("jdbc:postgresql://db.example.com:6543/stats?sslmode=require", url.jdbcUrl());
    }

    @Test
    void decodesSpecialCharactersInPassword() {
        PostgresUrl url = PostgresUrl.parse("postgresql://user:p%40ss%3Aword@host/db");
        assertEquals("p@ss:word", url.password());
    }

    @Test
    void acceptsJdbcUrlAsIs() {
        String jdbc = "jdbc:postgresql://localhost:5432/stats?user=me&password=pw";
        PostgresUrl url = PostgresUrl.parse(jdbc);
        assertEquals(jdbc, url.jdbcUrl());
        assertNull(url.username());
    }

    @Test
    void rejectsOtherFormatsWithoutLeakingThePassword() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> PostgresUrl.parse("mysql://user:topsecret@host/db"));
        assertFalse(e.getMessage().contains("topsecret"));
        assertFalse(PostgresUrl.parse("postgresql://u:topsecret@h/db").toString().contains("topsecret"));
    }
}
