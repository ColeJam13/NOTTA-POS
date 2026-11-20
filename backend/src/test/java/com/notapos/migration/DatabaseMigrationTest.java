package com.notapos.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the Flyway migrations themselves: the two ways a database can
 * meet them, and that both end in the same place.
 *
 *   1. A database from BEFORE Flyway: it already has every table and real
 *      data, but no record of migrations. This is the owner's database. Flyway
 *      must mark it as "already at V1" (not run V1 against it), apply V2
 *      onwards, and lose nothing.
 *   2. A BRAND-NEW, empty database: Flyway must run every file from V1.
 *
 * WHY THIS IS ITS OWN TEST: every other database test starts from an empty
 * database, which is case 2. Case 1 only happens once in real life - the first
 * start after Flyway was adopted - and getting it wrong means the backend
 * refuses to start against a database full of orders.
 *
 * No Spring here: this drives Flyway directly with the same two settings the
 * app uses, read from application.properties so the test cannot drift from them.
 *
 * @author CJ
 */
class DatabaseMigrationTest {

    private static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("postgres")
                    .withUsername("test")
                    .withPassword("test");

    private static boolean baselineOnMigrate;
    private static String baselineVersion;

    @BeforeAll
    static void startDatabaseAndReadSettings() throws IOException {
        postgres.start();

        Properties appSettings = new Properties();
        try (InputStream in = DatabaseMigrationTest.class.getResourceAsStream("/application.properties")) {
            appSettings.load(in);
        }
        baselineOnMigrate = Boolean.parseBoolean(appSettings.getProperty("spring.flyway.baseline-on-migrate"));
        baselineVersion = appSettings.getProperty("spring.flyway.baseline-version");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    // Make an empty database inside the container and return its address
    private String newDatabase(String name) throws SQLException {
        try (Connection admin = DriverManager.getConnection(postgres.getJdbcUrl(), "test", "test");
             Statement statement = admin.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + name);
            statement.execute("CREATE DATABASE " + name);
        }
        return postgres.getJdbcUrl().replaceFirst("/postgres(\\?|$)", "/" + name + "$1");
    }

    // Run Flyway the way the app does at start-up
    private void migrate(String jdbcUrl) {
        Flyway.configure()
                .dataSource(jdbcUrl, "test", "test")
                .baselineOnMigrate(baselineOnMigrate)
                .baselineVersion(baselineVersion)
                .load()
                .migrate();
    }

    private void run(String jdbcUrl, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "test", "test");
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        }
    }

    // One value from one row
    private String value(String jdbcUrl, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "test", "test");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getString(1) : null;
        }
    }

    // Every row of a query, each row joined into one line of text
    private List<String> rows(String jdbcUrl, String sql) throws SQLException {
        List<String> lines = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(jdbcUrl, "test", "test");
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            int columns = result.getMetaData().getColumnCount();
            while (result.next()) {
                StringBuilder line = new StringBuilder();
                for (int column = 1; column <= columns; column++) {
                    line.append(result.getString(column)).append(" | ");
                }
                lines.add(line.toString());
            }
        }
        return lines;
    }

    // The text of a migration file
    private String migrationFile(String name) throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/db/migration/" + name)) {
            assertNotNull(in, "missing migration file " + name);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * Build a database the way one looked before Flyway: all the tables, some
     * data, and no flyway_schema_history table.
     *
     * V1 was written from the structure of the owner's real database, so running
     * its SQL by hand (NOT through Flyway) gives exactly such a database.
     */
    private String databaseFromBeforeFlyway(String name) throws SQLException, IOException {
        String jdbcUrl = newDatabase(name);
        run(jdbcUrl, migrationFile("V1__baseline.sql"));

        // A table seeded the old way: the seed file never set is_quick_order, so it is NULL
        run(jdbcUrl, "INSERT INTO tables (table_number, seat_count, status, server_name, created_at) "
                + "VALUES ('W1', 2, 'occupied', 'CJ', now())");
        // A Quick Order made by the app, which does set the flag
        run(jdbcUrl, "INSERT INTO tables (table_number, seat_count, status, is_quick_order, created_at) "
                + "VALUES ('QO1', 0, 'available', true, now())");
        run(jdbcUrl, "INSERT INTO menu_items (name, price, category, is_active, created_at) "
                + "VALUES ('Chicken Cutty', 17.00, 'Savory', true, now())");
        run(jdbcUrl, "INSERT INTO orders (table_id, order_type, status, server_name, subtotal, tax, total, created_at) "
                + "VALUES (1, 'dine_in', 'open', 'CJ', 0, 0, 0, now())");
        run(jdbcUrl, "INSERT INTO order_items (order_id, menu_item_id, quantity, price, status, delay_seconds, is_locked, created_at) "
                + "VALUES (1, 1, 1, 17.00, 'pending', 15, true, now())");
        run(jdbcUrl, "INSERT INTO staff (name, role, pin_hash, is_active, created_at) "
                + "VALUES ('Morgan', 'manager', 'a-hash', true, now())");
        return jdbcUrl;
    }

    // What a database's structure is: every column with its type, whether it may be empty, and its default
    private List<String> structureOf(String jdbcUrl) throws SQLException {
        return rows(jdbcUrl, "SELECT table_name, column_name, data_type, character_maximum_length, numeric_precision, "
                + "numeric_scale, is_nullable, column_default, is_identity "
                + "FROM information_schema.columns "
                + "WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history' "
                + "ORDER BY table_name, column_name");
    }

    // ------------------------------------------------------------------
    // 1. A database from before Flyway (the owner's case)
    // ------------------------------------------------------------------

    @Test
    void testDatabaseFromBeforeFlyway_ShouldBeMarkedAtV1NotRebuilt() throws Exception {
        // WHAT: Test the first start against a database that already has tables and data
        // WHY: Running V1 here would fail ("table already exists") and the backend would not
        //      start. Flyway must record V1 as done and carry on from V2.

        // Given
        String jdbcUrl = databaseFromBeforeFlyway("before_flyway_baseline");

        // When
        migrate(jdbcUrl);

        // Then - version 1 is recorded as a baseline (not as a script that ran), and V2 ran
        assertEquals("BASELINE", value(jdbcUrl, "SELECT type FROM flyway_schema_history WHERE version = '1'"));
        assertEquals("SQL", value(jdbcUrl, "SELECT type FROM flyway_schema_history WHERE version = '2'"));
        assertEquals("t", value(jdbcUrl, "SELECT bool_and(success)::text FROM flyway_schema_history").substring(0, 1));
    }

    @Test
    void testDatabaseFromBeforeFlyway_ShouldKeepEveryRow() throws Exception {
        // WHAT: Test that nothing already in the database is lost
        // WHY: It holds the restaurant's orders and staff

        // Given
        String jdbcUrl = databaseFromBeforeFlyway("before_flyway_data");

        // When
        migrate(jdbcUrl);

        // Then
        assertEquals("2", value(jdbcUrl, "SELECT count(*) FROM tables"));
        assertEquals("1", value(jdbcUrl, "SELECT count(*) FROM menu_items"));
        assertEquals("1", value(jdbcUrl, "SELECT count(*) FROM orders"));
        assertEquals("1", value(jdbcUrl, "SELECT count(*) FROM order_items"));
        assertEquals("17.00", value(jdbcUrl, "SELECT price FROM order_items"));
        assertEquals("Morgan", value(jdbcUrl, "SELECT name FROM staff"));
        assertEquals("open", value(jdbcUrl, "SELECT status FROM orders"));
    }

    @Test
    void testV2_ShouldFillInTheQuickOrderFlagAndGiveItADefault() throws Exception {
        // WHAT: Test what V2 does to tables seeded before the flag had a default
        // WHY: Those rows held NULL, which once left the Floor Map empty

        // Given - W1 has NULL, QO1 has true
        String jdbcUrl = databaseFromBeforeFlyway("before_flyway_v2");
        assertNull(value(jdbcUrl, "SELECT is_quick_order::text FROM tables WHERE table_number = 'W1'"));

        // When
        migrate(jdbcUrl);

        // Then - the NULL became false, the real Quick Order is untouched
        assertEquals("false", value(jdbcUrl, "SELECT is_quick_order::text FROM tables WHERE table_number = 'W1'"));
        assertEquals("true", value(jdbcUrl, "SELECT is_quick_order::text FROM tables WHERE table_number = 'QO1'"));

        // And a row inserted without the column (as the seed file does) now gets false
        run(jdbcUrl, "INSERT INTO tables (table_number, seat_count, status, created_at) VALUES ('W2', 2, 'available', now())");
        assertEquals("false", value(jdbcUrl, "SELECT is_quick_order::text FROM tables WHERE table_number = 'W2'"));
    }

    @Test
    void testMigrate_ASecondStart_ShouldChangeNothing() throws Exception {
        // WHAT: Test starting the backend again after the migrations have run
        // WHY: Every start runs Flyway. A file that has run must never run again.

        // Given - migrated once
        String jdbcUrl = databaseFromBeforeFlyway("before_flyway_twice");
        migrate(jdbcUrl);
        List<String> historyAfterFirstStart = rows(jdbcUrl, "SELECT version, type, checksum FROM flyway_schema_history ORDER BY installed_rank");

        // When
        migrate(jdbcUrl);

        // Then
        assertEquals(historyAfterFirstStart, rows(jdbcUrl, "SELECT version, type, checksum FROM flyway_schema_history ORDER BY installed_rank"));
        assertEquals("2", value(jdbcUrl, "SELECT count(*) FROM tables"));
    }

    // ------------------------------------------------------------------
    // 2. A brand-new database
    // ------------------------------------------------------------------

    @Test
    void testBrandNewDatabase_ShouldRunEveryMigrationFromV1() throws Exception {
        // WHAT: Test the first start against an empty database
        // WHY: A new machine, CI, and every other database test start this way

        // Given
        String jdbcUrl = newDatabase("brand_new");

        // When
        migrate(jdbcUrl);

        // Then - V1 actually ran here (type SQL, not BASELINE) and created the tables
        assertEquals("SQL", value(jdbcUrl, "SELECT type FROM flyway_schema_history WHERE version = '1'"));
        assertEquals("12", value(jdbcUrl, "SELECT count(*) FROM information_schema.tables "
                + "WHERE table_schema = 'public' AND table_name <> 'flyway_schema_history'"));
    }

    // ------------------------------------------------------------------
    // Both roads lead to the same place
    // ------------------------------------------------------------------

    @Test
    void testBothKindsOfDatabase_ShouldEndUpWithTheSameStructure() throws Exception {
        // WHAT: Test that a database from before Flyway and a brand-new one are identical
        //       after migrating: same tables, columns, types, and defaults
        // WHY: This is the point of adopting Flyway. Before it, two databases could quietly
        //      differ (one had a default the other lacked) and behave differently.

        // Given
        String existing = databaseFromBeforeFlyway("compare_existing");
        String brandNew = newDatabase("compare_brand_new");

        // When
        migrate(existing);
        migrate(brandNew);

        // Then
        List<String> existingStructure = structureOf(existing);
        assertFalse(existingStructure.isEmpty());
        assertEquals(existingStructure, structureOf(brandNew));
    }
}
