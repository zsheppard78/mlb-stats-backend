package com.baseballstats.baseballstats;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** The app starts, and the database tables are created from the migrations. */
@SpringBootTest(properties = "DATABASE_URL=") // always the in-memory database, never Neon
class BaseballStatsApplicationTests {

    @Test
    void contextLoads() {
    }

}
