package com.gitracker.repository;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FlywayMigrationIntegrationTest {

    private HikariDataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://localhost:5435/gitracker_test_migration");
        config.setUsername("postgres");
        config.setPassword("postgres");
        config.setDriverClassName("org.postgresql.Driver");
        dataSource = new HikariDataSource(config);
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP SCHEMA public CASCADE; CREATE SCHEMA public;");
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Test
    void shouldExecuteFlywayEngineMigrationsAndRecordHistoryProperly() {
        // 1. Configure and instantiate real Flyway engine against isolated PostgreSQL database
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .cleanDisabled(true)
                .baselineOnMigrate(false)
                .load();

        // 2. Execute migration using Flyway API
        MigrateResult initialResult = flyway.migrate();

        // 3. Verify all 5 migrations were executed and marked successful by Flyway engine
        assertTrue(initialResult.success, "Flyway migration execution must succeed");
        assertEquals(5, initialResult.migrationsExecuted, "Exactly 5 migrations (V1-V5) should be executed initially");
        assertEquals("5", initialResult.targetSchemaVersion, "Target schema version should be 5");

        // 4. Verify flyway_schema_history table was created by Flyway
        Integer historyTableExists = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'flyway_schema_history'",
                Integer.class
        );
        assertEquals(1, historyTableExists, "flyway_schema_history table must exist");

        // 5. Query flyway_schema_history and verify order, versions, success, and checksums
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT installed_rank, version, description, type, script, checksum, success FROM flyway_schema_history ORDER BY installed_rank"
        );
        assertEquals(5, rows.size(), "flyway_schema_history should contain 5 records");

        for (int i = 0; i < 5; i++) {
            Map<String, Object> row = rows.get(i);
            int installedRank = (int) row.get("installed_rank");
            String version = (String) row.get("version");
            Boolean success = (Boolean) row.get("success");
            Object checksum = row.get("checksum");

            assertEquals(i + 1, installedRank, "Migrations must execute in sequential order");
            assertEquals(String.valueOf(i + 1), version, "Migration versions must match sequential numbering (1..5)");
            assertTrue(success, "Every migration must be marked as successful in history table");
            assertNotNull(checksum, "Every migration must have an associated checksum calculated by Flyway");
        }

        // 6. Verify Flyway info API
        MigrationInfo[] appliedMigrations = flyway.info().applied();
        assertEquals(5, appliedMigrations.length);
        assertEquals("5", flyway.info().current().getVersion().getVersion());

        // 7. Test Idempotence: Rerunning Flyway should execute 0 new migrations
        MigrateResult rerunResult = flyway.migrate();
        assertTrue(rerunResult.success);
        assertEquals(0, rerunResult.migrationsExecuted, "Rerunning Flyway must not reapply already applied migrations");

        // 8. Verify history table record count is unchanged after rerun
        Integer historyCountAfterRerun = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM flyway_schema_history", Integer.class);
        assertEquals(5, historyCountAfterRerun, "History table count should remain unchanged after idempotent rerun");
    }
}
