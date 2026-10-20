package com.baseballstats.baseballstats.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Database connection.
 *
 * On Render, set the DATABASE_URL environment variable to the connection string from Neon
 * (postgresql://user:password@host/dbname?sslmode=require). It's converted for the Java driver here.
 * When DATABASE_URL isn't set (for example on your laptop), Spring Boot falls back to an
 * in-memory H2 database, so the app still runs without any setup.
 *
 * Tables are created and updated by Flyway from src/main/resources/db/migration.
 */
@Configuration
public class DatabaseConfig {

    @Bean
    @ConditionalOnProperty(name = "DATABASE_URL")
    public DataSource dataSource(@Value("${DATABASE_URL}") String databaseUrl) {
        PostgresUrl url = PostgresUrl.parse(databaseUrl);

        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName("neon");
        dataSource.setJdbcUrl(url.jdbcUrl());
        if (url.username() != null) dataSource.setUsername(url.username());
        if (url.password() != null) dataSource.setPassword(url.password());

        // A small pool is plenty for this app and stays well within Neon's free plan.
        dataSource.setMaximumPoolSize(5);
        // Close idle connections after a minute so Neon can scale to zero (and stop using
        // free compute hours) while nobody is on the site.
        dataSource.setMinimumIdle(0);
        dataSource.setIdleTimeout(60_000);
        // Neon wakes up in well under a second; this leaves plenty of room.
        dataSource.setConnectionTimeout(30_000);
        return dataSource;
    }
}
