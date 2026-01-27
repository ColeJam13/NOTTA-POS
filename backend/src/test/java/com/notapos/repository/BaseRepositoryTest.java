package com.notapos.repository;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for all repository tests using Testcontainers.
 * 
 * WHAT: Provides a shared PostgreSQL Docker container for all repository tests
 * WHY: Tests run against real PostgreSQL database behavior, not mocks or H2
 * 
 * HOW IT WORKS:
 * 1. Starts PostgreSQL 16 container ONCE before any tests run
 * 2. Container is shared across ALL test classes (singleton pattern)
 * 3. Each test method gets a fresh transaction (rolled back after test)
 * 4. Container stops automatically when JVM exits
 * 
 * SINGLETON PATTERN:
 * The container is created as a static field WITHOUT @Container annotation.
 * This ensures only ONE container is created for the entire test suite,
 * preventing port conflicts and connection issues.
 * 
 * USAGE:
 * All repository test classes should extend this base class:
 * 
 * class MenuItemRepositoryTest extends BaseRepositoryTest {
 *     // Your tests here
 * }
 * 
 * @author CJ
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class BaseRepositoryTest {

    /**
     * Shared PostgreSQL container for ALL tests - SINGLETON PATTERN.
     * 
     * Using PostgreSQL 16 to match production database.
     * Container is started once and reused across all test classes.
     * 
     * NOTE: Not using @Container annotation - we manage lifecycle manually
     * to ensure true singleton behavior across all test classes.
     */
    private static final PostgreSQLContainer<?> postgres;

    // Static initializer block - runs once when class is loaded
    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("notta_pos_test")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
    }

    /**
     * Configure Spring to use the Testcontainers PostgreSQL database.
     * 
     * This dynamically sets the datasource properties to point to the
     * shared Docker container.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    /**
     * Hook for subclasses to set up test data.
     * 
     * Override this method in your test class to create test entities.
     * This is called before each test method.
     */
    @BeforeEach
    public void baseSetUp() {
        // Subclasses can override this to add their own setup
        // Each test gets a fresh transaction that rolls back automatically
    }
}