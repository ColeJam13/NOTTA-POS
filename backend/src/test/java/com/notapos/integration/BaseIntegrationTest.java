package com.notapos.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import com.notapos.testsupport.SignIn;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for integration tests: the WHOLE backend, started for real,
 * talking to a real PostgreSQL database in a Docker container.
 *
 * WHAT: The other kinds of test each check one layer with the rest faked:
 *         - service tests (Mockito) fake the database
 *         - controller tests (@WebMvcTest) fake the services
 *         - repository tests (BaseRepositoryTest) use a real database but no web layer
 *       An integration test sends a real HTTP request through the real
 *       controller, service, and repository, and looks at what comes back and
 *       what ended up in the database.
 * WHY:  Some defects only exist between layers. "The price the browser sent
 *       was saved" is one: every layer does what its own tests expect, and the
 *       result is still wrong. Rules about what a request must NOT be able to
 *       do are best proven from the outside, the way an attacker or a stale
 *       screen would try them.
 *
 * HOW IT WORKS:
 *   - @SpringBootTest starts the full application (all controllers, services,
 *     the security filter chain, the scheduler).
 *   - @AutoConfigureMockMvc provides MockMvc, which sends requests to it
 *     without opening a network port.
 *   - The container is started once and shared by every integration test
 *     class (the same singleton pattern as BaseRepositoryTest).
 *
 * These tests are slower than the others (the application starts once, a few
 * seconds). Use them for rules that cross layers, not for every method.
 *
 * SIGNING IN: every request needs a signed-in person (step A3b of the plan).
 * A test says who is asking the same way a controller test does, with the
 * SignIn helper:
 *
 *     mockMvc.perform(post("/api/orders").with(signIn.asServer()) ...)
 *
 * That starts a real session without going through the PIN pad. Tests ABOUT
 * signing in (SignInRulesIntegrationTest, RoleMatrixIntegrationTest) create
 * real staff and sign in with real PINs instead.
 *
 * Tests are NOT wrapped in a transaction that rolls back, because the request
 * runs in its own transactions like it does in production. Each test class
 * cleans the tables it uses in a @BeforeEach.
 *
 * @author CJ
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(SignIn.class)                   // The test helper is not part of the app, so it is added here
public abstract class BaseIntegrationTest {

    private static final PostgreSQLContainer<?> postgres;

    // Static initializer block - runs once when the class is loaded
    static {
        postgres = new PostgreSQLContainer<>("postgres:16-alpine")
                .withDatabaseName("notta_pos_integration")
                .withUsername("test")
                .withPassword("test");
        postgres.start();
    }

    // Point the application at the container instead of the developer's own database
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // No ddl-auto override: the tables are created by the Flyway migrations, the same way
        // as in the real app, and Hibernate only validates them. Every test run therefore
        // also proves that the migrations and the entity classes agree.
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected SignIn signIn;            // Says who a request comes from: .with(signIn.asServer())
}
