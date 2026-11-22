package com.notapos.integration;

import com.notapos.config.RestaurantTime;
import com.notapos.enums.StaffRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Pins down the JSON the screens depend on: the exact answers of the main
 * endpoints, from a database holding known rows.
 *
 * WHY THIS EXISTS (step U1 of the Phase 2 plan): the backend turns its objects
 * into JSON with a library (Jackson) that the app never configures; it relies
 * on the library's defaults. Spring Boot 4 brings a new major version of that
 * library with different defaults. A changed default could rename a field,
 * write a date as a number, or drop a null, and every other test would stay
 * green, because they check values one field at a time and do not notice a
 * field they never asked for. The screens would be the first to find out.
 *
 * These tests were written and recorded on Spring Boot 3.5, BEFORE the
 * upgrade. They must pass unchanged after it. That is the proof that an
 * upgrade changed nothing a screen can see.
 *
 * HOW IT WORKS
 *   1. setUp() empties the tables and inserts a small, fixed set of rows with
 *      plain SQL: fixed IDs (9000 and up) and fixed dates, so every answer is
 *      the same on every run.
 *   2. Each case in recordedAnswers() sends one request and compares the whole
 *      answer with a file in src/test/resources/json-shape/. The comparison is
 *      STRICT: every field must be there with the same value, no field may be
 *      added, and lists must be in the same order. Only the order of the
 *      fields inside an object does not matter, as it does not to a screen.
 *   3. The tests further down cover answers that hold something different each
 *      time (a new token, the time of an error), and the known oddities, which
 *      are checked letter for letter.
 *
 * WHEN AN ANSWER IS MEANT TO CHANGE (a new field on a screen's data, say), make
 * the change, then record the files again and read the difference in git
 * before committing it:
 *
 *     mvn test -Dtest=JsonShapeIntegrationTest -Dnotta.json.record=true
 *
 * Never record again to make a failure go away without reading what changed.
 *
 * @author CJ
 */
class JsonShapeIntegrationTest extends BaseIntegrationTest {

    /**
     * For this class only, the backend's clock stands still at 18:00 UTC on the day the
     * fixed rows below are dated.
     *
     * WHY: A person's notifications are "the last 24 hours". With the real clock, a
     * notification inserted with a fixed time would be listed today and gone tomorrow,
     * and the recorded answer could not stay the same. With the clock held here, the
     * answer is the same on any day the tests run.
     *
     * @Primary makes this the Clock that is handed to everything asking for one, in
     * place of TimeConfig's (which is still there, unused). Nothing else recorded here
     * depends on what time it is now.
     */
    @TestConfiguration
    static class ClockHeldStill {
        @Bean
        @Primary
        Clock clockHeldOnTheDayOfTheFixedRows() {
            return Clock.fixed(Instant.parse("2026-11-18T18:00:00Z"), ZoneOffset.UTC);
        }

        // A moment stored in the database (a notification's time, a challenge's start and
        // end) is sent to a screen as the restaurant's local time. Which local time that
        // is depends on the time zone of the machine the backend runs on, and a recorded
        // answer must be the same on every machine. So here the restaurant is in UTC.
        @Bean
        @Primary
        RestaurantTime restaurantTimeReadInUtc(Clock clock) {
            return new RestaurantTime(clock, ZoneOffset.UTC);
        }
    }

    // Where the recorded answers live, as the tests read them and as "record" writes them
    private static final String RECORDED_ON_CLASSPATH = "/json-shape/";
    private static final Path RECORDED_IN_SOURCE = Path.of("src", "test", "resources", "json-shape");

    // A date and time exactly as the backend writes one: 2026-11-18T12:30:00, with or
    // without a fraction of a second, and no time zone
    private static final String LOCAL_DATE_TIME = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?$";

    @Autowired private JdbcTemplate jdbc;

    // The people asking. IDs match the staff rows below.
    private RequestPostProcessor morgan;        // Manager, staff 9002
    private RequestPostProcessor sarah;         // Server, staff 9007. She opened orders 9001 and 9002.

    @BeforeEach
    void setUp() {
        // Empty every table (nothing rolls back by itself in an integration test)
        for (String table : List.of("order_item_modifiers", "order_items", "payments", "orders",
                "menu_item_modifier_groups", "modifiers", "modifier_groups", "menu_items", "prep_stations",
                "table_status_logs", "tables", "staff_sessions", "staff", "settings",
                "notification_dismissals", "notifications", "challenge_awards", "challenges")) {
            jdbc.update("DELETE FROM " + table);
        }

        // ----- The menu -----
        jdbc.update("INSERT INTO prep_stations (prep_station_id, name, description, is_active, created_at) "
                + "VALUES (9001, 'Grill', 'Hot line', true, '2026-11-18 09:00:00')");
        jdbc.update("INSERT INTO menu_items (menu_item_id, name, description, category, price, prep_station_id, is_active, is_86d, created_at) VALUES "
                + "(9001, 'Omelette of the Day', 'Three eggs', 'Savory', 16.00, 9001, true, false, '2026-11-18 09:00:00.123456'), "   // A time with a fraction of a second
                + "(9002, 'Garden Toast', NULL, 'Savory', 12.50, NULL, true, true, '2026-11-18 09:00:00'), "                           // 86'd, no station, no description
                + "(9003, 'Old Special', NULL, 'Savory', 9.00, NULL, false, false, '2026-11-18 09:00:00')");                           // Off the menu
        jdbc.update("INSERT INTO modifier_groups (modifier_group_id, name, description, is_required, max_selections, is_active, created_at) "
                + "VALUES (9001, 'Side', 'Choose one', true, 1, true, '2026-11-18 09:00:00')");
        jdbc.update("INSERT INTO modifiers (modifier_id, modifier_group_id, name, price_adjustment, is_active, created_at) VALUES "
                + "(9001, 9001, 'Hashbrowns', 0.00, true, '2026-11-18 09:00:00'), "
                + "(9002, 9001, 'French Toast', 2.00, true, '2026-11-18 09:00:00')");
        jdbc.update("INSERT INTO menu_item_modifier_groups (menu_item_modifier_group_id, menu_item_id, modifier_group_id, created_at) "
                + "VALUES (9001, 9001, 9001, '2026-11-18 09:00:00')");

        // ----- The floor: two tables and a Quick Order -----
        jdbc.update("INSERT INTO tables (table_id, table_number, section, shape, seat_count, x_position, y_position, rotation, status, server_name, is_quick_order, created_at) VALUES "
                + "(9001, 'W1', 'Window', 'square', 4, 100, 120, 45, 'occupied', 'Sarah', false, '2026-11-18 09:00:00'), "
                + "(9002, 'W2', 'Window', 'round', 2, 220, 120, 0, 'available', NULL, false, '2026-11-18 09:00:00'), "
                + "(9003, 'QO1', 'Quick Orders', NULL, 0, NULL, NULL, NULL, 'available', NULL, true, '2026-11-18 09:00:00')");

        // ----- Staff (PINs 2222 and 1111) -----
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(6);
        jdbc.update("INSERT INTO staff (staff_id, name, role, pin_hash, is_active, must_change_pin, created_at) VALUES "
                + "(9002, 'Morgan', 'manager', ?, true, false, '2026-11-18 08:00:00'), "
                + "(9007, 'Sarah', 'server', ?, true, false, '2026-11-18 08:00:00'), "
                + "(9008, 'Jamie', 'host', ?, false, false, '2026-11-18 08:00:00')",
                encoder.encode("2222"), encoder.encode("1111"), encoder.encode("3333"));

        // ----- An open check on W1: one item sent with a modifier and a note, one still a draft -----
        jdbc.update("INSERT INTO orders (order_id, table_id, order_type, status, server_name, staff_id, subtotal, tax, total, created_at, closed_at, version) VALUES "
                + "(9001, 9001, 'dine_in', 'open', 'Sarah', 9007, 0.00, 0.00, 0.00, '2026-11-18 12:30:00', NULL, 0), "
                // ----- A paid check on W2, opened by Sarah -----
                + "(9002, 9002, 'dine_in', 'closed', 'Sarah', 9007, 18.00, 0.54, 18.54, '2026-11-18 11:00:00', '2026-11-18 11:45:30.5', 1), "
                // ----- A paid Quick Order, opened by someone else (staff 9099) -----
                + "(9003, 9003, 'takeout', 'closed', 'Dan', 9099, 12.50, 0.38, 12.88, '2026-11-18 11:50:00', '2026-11-18 11:52:00', 1), "
                // ----- A paid check on W2 opened by someone else: Sarah is not sent its tip -----
                + "(9004, 9002, 'dine_in', 'closed', 'Dan', 9099, 16.00, 0.48, 16.48, '2026-11-18 10:00:00', '2026-11-18 10:40:00', 1)");
        jdbc.update("INSERT INTO order_items (order_item_id, order_id, menu_item_id, quantity, price, status, is_locked, delay_seconds, "
                + "special_instructions, ring_in_position, created_at, sent_at, delay_expires_at, fired_at, completed_at) VALUES "
                + "(9101, 9001, 9001, 1, 18.00, 'pending', true, 16, 'no onions', 0, '2026-11-18 12:30:10', '2026-11-18 12:30:20', '2026-11-18 12:30:36', NULL, NULL), "
                + "(9102, 9001, 9002, 2, 12.50, 'draft', false, 16, NULL, 1, '2026-11-18 12:31:00', NULL, NULL, NULL, NULL), "
                + "(9103, 9002, 9001, 1, 18.00, 'completed', true, 16, NULL, 0, '2026-11-18 11:00:10', '2026-11-18 11:00:20', '2026-11-18 11:00:36', '2026-11-18 11:05:00', '2026-11-18 11:15:00'), "
                + "(9104, 9003, 9002, 1, 12.50, 'completed', true, 16, NULL, 0, '2026-11-18 11:50:10', '2026-11-18 11:50:20', '2026-11-18 11:50:36', '2026-11-18 11:51:00', '2026-11-18 11:51:30'), "
                + "(9105, 9004, 9001, 1, 16.00, 'completed', true, 16, NULL, 0, '2026-11-18 10:00:10', '2026-11-18 10:00:20', '2026-11-18 10:00:36', '2026-11-18 10:05:00', '2026-11-18 10:15:00')");
        jdbc.update("INSERT INTO order_item_modifiers (order_item_modifier_id, order_item_id, modifier_id, price_adjustment, created_at) VALUES "
                + "(9201, 9101, 9002, 2.00, '2026-11-18 12:30:10'), "
                + "(9202, 9103, 9002, 2.00, '2026-11-18 11:00:10')");
        jdbc.update("INSERT INTO payments (payment_id, order_id, amount, tip_amount, payment_method, status, transaction_reference, created_at) VALUES "
                + "(9301, 9002, 18.54, 4.00, 'credit_card', 'completed', NULL, '2026-11-18 11:45:30'), "
                + "(9302, 9003, 12.88, 0.00, 'cash', 'completed', NULL, '2026-11-18 11:52:00'), "
                + "(9303, 9004, 16.48, 3.25, 'cash', 'completed', NULL, '2026-11-18 10:40:00')");

        // ----- Notifications: an 86 and a message for everyone, an "order up" for Sarah, and one she dismissed -----
        jdbc.update("INSERT INTO notifications (notification_id, kind, body, recipient_staff_id, created_by_staff_id, created_by_name, created_at) VALUES "
                + "(9401, 'item_86', 'Garden Toast is 86''d', NULL, 9099, 'Dan', '2026-11-18 16:00:00+00'), "
                + "(9402, 'order_up', 'Table W1 is up', 9007, 9099, 'Dan', '2026-11-18 16:30:00.25+00'), "              // A time with a fraction of a second
                + "(9403, 'message', 'Fryer is down for 10 minutes', NULL, 9099, 'Dan', '2026-11-18 17:00:00+00'), "
                + "(9404, 'item_back', 'Garden Toast is back on', NULL, 9099, 'Dan', '2026-11-18 17:30:00+00'), "       // Sarah dismissed this one
                + "(9405, 'message', 'A system notice', NULL, NULL, NULL, '2026-11-18 15:00:00+00')");                  // From nobody on record
        jdbc.update("INSERT INTO notification_dismissals (notification_id, staff_id, dismissed_at) VALUES (9404, 9007, '2026-11-18 17:45:00+00')");

        // ----- Challenges, as they stand at the moment the clock is held at (18:00 UTC on the 18th): -----
        //   9501 running: sell 5 from Savory. The three paid checks above were closed in its period.
        //   9502 finished at noon: reach 15.00 in sales. Both people completed it; Sarah's prize was handed over.
        //   9503 not started: a check average, with the fields the other two leave empty.
        jdbc.update("INSERT INTO challenges (challenge_id, name, description, measure, target, menu_category, menu_item_id, min_checks, "
                + "starts_at, ends_at, prize, created_by_staff_id, created_by_name, created_at) VALUES "
                + "(9501, 'Savory Sprint', 'Sell five savory plates', 'items_sold', 5.00, 'Savory', NULL, NULL, "
                + "'2026-11-18 05:00:00+00', '2026-11-19 05:00:00+00', '$20', 9002, 'Morgan', '2026-11-18 04:00:00+00'), "
                + "(9502, 'Morning Rush', NULL, 'total_sales', 15.00, NULL, NULL, NULL, "
                + "'2026-11-18 05:00:00+00', '2026-11-18 12:00:00+00', 'First cut Friday', 9002, 'Morgan', '2026-11-18 04:00:00+00'), "
                + "(9503, 'High Average', NULL, 'check_average', 40.00, NULL, NULL, 3, "
                + "'2026-11-19 05:00:00+00', '2026-11-20 05:00:00.5+00', NULL, 9002, 'Morgan', '2026-11-18 04:00:00+00')");
        jdbc.update("INSERT INTO challenge_awards (challenge_award_id, challenge_id, staff_id, staff_name, awarded_by_staff_id, awarded_by_name, awarded_at) VALUES "
                + "(9601, 9502, 9007, 'Sarah', 9002, 'Morgan', '2026-11-18 13:00:00.25+00')");                          // A time with a fraction of a second

        morgan = signIn.as(9002L, "Morgan", StaffRole.MANAGER);
        sarah = signIn.as(9007L, "Sarah", StaffRole.SERVER);
    }

    // ------------------------------------------------------------------
    // The recorded answers
    // ------------------------------------------------------------------

    // One request whose whole answer is kept in a file
    private record Case(String file, String asWhom, String path) {
        @Override
        public String toString() {                                  // The test's name in the report
            return file + ": GET " + path + " as " + asWhom;
        }
    }

    static List<Case> recordedAnswers() {
        return List.of(
                // The order screen's menu
                new Case("menu-items", "server", "/api/menu-items"),
                new Case("menu-items-86", "server", "/api/menu-items/86"),
                new Case("menu-item-modifier-groups", "server", "/api/menu-items/modifier-groups"),
                new Case("prep-stations", "server", "/api/prep-stations"),
                // The Floor Map
                new Case("tables", "server", "/api/tables"),
                // Tables and Quick Orders. A manager is sent every tip; a server only her own.
                new Case("tables-overview-as-manager", "manager", "/api/tables/overview?quickOrders=false"),
                new Case("tables-overview-as-server", "server", "/api/tables/overview?quickOrders=false"),
                new Case("quick-orders-overview-as-manager", "manager", "/api/tables/overview?quickOrders=true"),
                // The kitchen display and Active Orders
                new Case("active-orders", "server", "/api/orders/active"),

                // The search box. Two searches, to record a result of each kind the fixed
                // rows can give: a table and an open check, then a menu item. (A closed check
                // in a search answer is the same shape as on the Tables screen, recorded
                // above; none is found here because search only looks back from the real
                // "today", and the fixed rows are dated.)
                new Case("search-table-and-open-check", "server", "/api/search?q=w1"),
                new Case("search-menu-item", "server", "/api/search?q=toast"),
                // The order screen reopening a check, and the Payment window
                new Case("order", "server", "/api/orders/9001"),
                new Case("orders-for-table", "server", "/api/orders/table/9001"),
                new Case("order-items", "server", "/api/order-items/order/9001"),
                new Case("order-item-modifiers", "server", "/api/order-item-modifiers/order/9001"),
                new Case("order-check", "server", "/api/orders/9001/check"),
                new Case("payments-for-order", "manager", "/api/payments/order/9002"),
                new Case("settings", "server", "/api/settings"),
                // The bell in the top bar: the signed-in person's own notifications
                new Case("notifications", "server", "/api/notifications"),
                // Financials
                new Case("financials", "manager", "/api/reports/financials?from=2026-11-18T00:00:00&to=2026-11-19T00:00:00"),
                // The leaderboard: what each person sold that day. Read as a server, because
                // every role may. One of the two people has no staff row (9099), so the
                // recording also holds the name taken from the check.
                new Case("staff-sales", "server", "/api/reports/staff-sales?from=2026-11-18T00:00:00&to=2026-11-19T00:00:00"),
                // Challenges, with everyone's progress: one running, one not started, one finished
                // with a prize handed over. Read as a server, because every role may.
                new Case("challenges", "server", "/api/challenges"),
                // Staff
                new Case("staff", "manager", "/api/staff"),
                new Case("staff-me", "server", "/api/staff/me"));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recordedAnswers")
    void testAnswer_ShouldBeExactlyWhatWasRecorded(Case recorded) throws Exception {
        // WHAT: One request, and its whole answer compared with the recorded file
        // WHY: See the top of this file

        RequestPostProcessor who = recorded.asWhom().equals("manager") ? morgan : sarah;
        String answer = mockMvc.perform(get(recorded.path()).with(who))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        if (Boolean.getBoolean("notta.json.record")) {              // -Dnotta.json.record=true: write the file and stop
            Files.createDirectories(RECORDED_IN_SOURCE);
            Files.writeString(RECORDED_IN_SOURCE.resolve(recorded.file() + ".json"), answer + "\n");
            return;
        }

        mockMvc.perform(get(recorded.path()).with(who))
                .andExpect(content().json(recordedAnswer(recorded.file()), JsonCompareMode.STRICT));
    }

    private static String recordedAnswer(String file) throws IOException {
        try (InputStream in = JsonShapeIntegrationTest.class.getResourceAsStream(RECORDED_ON_CLASSPATH + file + ".json")) {
            assertNotNull(in, "No recorded answer json-shape/" + file + ".json. Record it: see the top of this file.");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ------------------------------------------------------------------
    // The known oddities, letter for letter
    // ------------------------------------------------------------------
    // The comparison above reads numbers as numbers and does not care how they are
    // written. These tests read the answer as plain text.

    private ResultActions asServer(String path) throws Exception {
        return mockMvc.perform(get(path).with(sarah)).andExpect(status().isOk());
    }

    @Test
    void testFieldNames_ShouldKeepTheirOddSpellings() throws Exception {
        // WHAT: The field names the frontend is written against, odd ones included
        // WHY: The names come from the getters (getXPosition, getIs86d, getIsQuickOrder),
        //      by rules inside the JSON library. A new version with a different rule
        //      would rename them, and the Floor Map would draw every table at 0,0.

        asServer("/api/tables")
                .andExpect(content().string(containsString("\"xposition\":100")))       // Not "xPosition" (in CLAUDE.md as a known quirk)
                .andExpect(content().string(containsString("\"yposition\":120")))
                .andExpect(content().string(not(containsString("xPosition"))))
                .andExpect(content().string(containsString("\"isQuickOrder\":false")))
                .andExpect(content().string(containsString("\"tableNumber\":\"W1\"")));

        asServer("/api/menu-items")
                .andExpect(content().string(containsString("\"is86d\":true")))
                .andExpect(content().string(containsString("\"isActive\":true")));

        asServer("/api/order-items/order/9001")
                .andExpect(content().string(containsString("\"isLocked\":true")))
                .andExpect(content().string(containsString("\"ringInPosition\":1")));
    }

    @Test
    void testMoney_ShouldBeWrittenAsANumberWithItsCents() throws Exception {
        // WHAT: How amounts are written: 16.00, not 16 or "16.00" or 1.6E+1
        // WHY: The screens call .toFixed(2) on these. A string would throw there.

        asServer("/api/menu-items")
                .andExpect(content().string(containsString("\"price\":16.00")))
                .andExpect(content().string(containsString("\"price\":12.50")));
        asServer("/api/orders/9001/check")
                .andExpect(content().string(containsString("\"subtotal\":18.00")))      // Only the sent item is on the check
                .andExpect(content().string(containsString("\"tax\":0.54")))
                .andExpect(content().string(containsString("\"total\":18.54")));
        asServer("/api/settings")
                .andExpect(content().string(containsString("\"taxRate\":0.03")));
    }

    @Test
    void testDates_ShouldBeWrittenAsLocalDateAndTimeText() throws Exception {
        // WHAT: How a moment is written: "2026-11-18T12:30:00", as text, with no zone
        // WHY: services/serverClock.js compares these with the X-Server-Time header, which
        //      is in the same form. A date written as a number or as a list of numbers
        //      ([2026,11,18,12,30]) would break every countdown and "5m ago".

        asServer("/api/orders/9001")
                .andExpect(content().string(containsString("\"createdAt\":\"2026-11-18T12:30:00\"")))
                .andExpect(content().string(containsString("\"closedAt\":null")));       // An empty date is there, as null
        asServer("/api/order-items/order/9001")
                .andExpect(content().string(containsString("\"sentAt\":\"2026-11-18T12:30:20\"")))
                .andExpect(content().string(containsString("\"delayExpiresAt\":\"2026-11-18T12:30:36\"")));
        // A fraction of a second is kept as it is stored, with no zeros added or removed
        asServer("/api/menu-items")
                .andExpect(content().string(containsString("\"createdAt\":\"2026-11-18T09:00:00.123456\"")));
        mockMvc.perform(get("/api/tables/overview").param("quickOrders", "false").with(morgan))
                .andExpect(content().string(containsString("\"closedAt\":\"2026-11-18T11:45:30.5\"")));
    }

    @Test
    void testAHiddenTip_ShouldBeLeftOutAndAnEmptyFieldElsewhereShouldBeNull() throws Exception {
        // WHAT: The two ways an answer says "nothing here"
        // WHY: Almost every empty field is sent as null ("description":null), and screens
        //      rely on the field being there. The tip on somebody else's check is the one
        //      exception: it is left out on purpose (ClosedCheckView). A new library
        //      default that dropped every null, or wrote every one, would break one of
        //      the two.

        asServer("/api/menu-items")
                .andExpect(content().string(containsString("\"description\":null")))
                .andExpect(content().string(containsString("\"prepStationId\":null")));

        asServer("/api/tables/overview?quickOrders=false")
                .andExpect(jsonPath("$.closedChecks[?(@.orderId == 9002)].tipAmount").value(4.00))   // Her own check
                .andExpect(jsonPath("$.closedChecks[?(@.orderId == 9004)]").exists())               // Dan's check is listed...
                .andExpect(jsonPath("$.closedChecks[?(@.orderId == 9004)].tipAmount").doesNotExist())   // ...without these two
                .andExpect(jsonPath("$.closedChecks[?(@.orderId == 9004)].paymentMethod").doesNotExist())
                .andExpect(content().string(not(containsString("3.25"))));              // Dan's tip is nowhere in what she is sent
    }

    // ------------------------------------------------------------------
    // Answers that are different every time
    // ------------------------------------------------------------------

    @Test
    void testSignIn_ShouldAnswerWithThePersonAndAToken() throws Exception {
        // WHAT: The answer to a right PIN: which fields, and nothing else
        // WHY: App.jsx keeps "staff" and "token" from this answer. The token is new each
        //      time, so the answer cannot be compared with a file.

        mockMvc.perform(post("/api/staff/sign-in").contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"1111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder("staff", "token")))
                .andExpect(jsonPath("$.staff.keys()", containsInAnyOrder("staffId", "name", "role", "isActive", "mustChangePin")))
                .andExpect(jsonPath("$.staff.staffId").value(9007))
                .andExpect(jsonPath("$.staff.name").value("Sarah"))
                .andExpect(jsonPath("$.staff.role").value("server"))
                .andExpect(jsonPath("$.staff.isActive").value(true))
                .andExpect(jsonPath("$.staff.mustChangePin").value(false))
                .andExpect(jsonPath("$.token", matchesPattern("^[A-Za-z0-9_-]{20,}$")))
                .andExpect(content().string(not(containsString("pin"))));               // Neither the PIN nor its hash, under any name ("mustChangePin" has a capital P)
    }

    // Every refusal has the same six fields (ApiError), whoever wrote it
    private static final String[] API_ERROR_FIELDS = {"timestamp", "status", "error", "message", "fieldErrors", "code"};

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void testARefusalWithNoSignIn_ShouldBeAnApiError() throws Exception {
        // WHAT: 401, written by RefusalResponder from the security filter chain
        // WHY: This body is written by hand, outside the usual path a controller's answer
        //      takes, so it could change on its own

        mockMvc.perform(get("/api/tables"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder(API_ERROR_FIELDS)))
                .andExpect(jsonPath("$.timestamp", matchesPattern(LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Sign in to do that."))
                .andExpect(jsonPath("$.fieldErrors").value(nullValue()))
                .andExpect(jsonPath("$.code").value(nullValue()))
                .andExpect(content().string(containsString("\"fieldErrors\":null")))    // There, as null: not left out
                .andExpect(content().string(containsString("\"code\":null")));
    }

    @Test
    void testARefusalForTheWrongRole_ShouldBeAnApiError() throws Exception {
        // WHAT: 403, built by GlobalExceptionHandler

        mockMvc.perform(get("/api/staff").with(sarah))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder(API_ERROR_FIELDS)))
                .andExpect(jsonPath("$.timestamp", matchesPattern(LOCAL_DATE_TIME)))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"))
                .andExpect(jsonPath("$.message").value("Your role (server) is not allowed to do that."));
    }

    @Test
    void testSomethingNotFound_ShouldBeAnApiError() throws Exception {
        // WHAT: 404 for a check that does not exist

        mockMvc.perform(get("/api/orders/999999/check").with(sarah))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder(API_ERROR_FIELDS)))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.fieldErrors").value(nullValue()));
    }

    @Test
    void testABadlyFormedRequest_ShouldBeAnApiErrorNamingEachField() throws Exception {
        // WHAT: 400 for a PIN that is not four digits
        // WHY: The Staff screen shows the messages in "fieldErrors" under the form

        mockMvc.perform(json(post("/api/staff/sign-in"), "{\"pin\":\"12\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder(API_ERROR_FIELDS)))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.fieldErrors.keys()", containsInAnyOrder("pin")))
                .andExpect(jsonPath("$.fieldErrors.pin").value("PIN must be exactly 4 digits"));
    }

    @Test
    void testARefusalAScreenReactsTo_ShouldCarryItsCode() throws Exception {
        // WHAT: 409 with "code":"ORDER_NOT_OPEN" for ringing an item onto a paid check
        // WHY: The order screen looks for this exact word (services/api.js) to tell the
        //      server the check was closed on another terminal

        mockMvc.perform(json(post("/api/order-items").with(sarah),
                        "{\"orderId\":9002,\"menuItemId\":9001,\"quantity\":1,\"modifierIds\":[9001],\"ringInPosition\":5}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.keys()", containsInAnyOrder(API_ERROR_FIELDS)))
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.code").value("ORDER_NOT_OPEN"));
    }

    // ------------------------------------------------------------------
    // What a request may contain
    // ------------------------------------------------------------------

    @Test
    void testARequestWithFieldsTheBackendDoesNotKnow_ShouldStillBeAccepted() throws Exception {
        // WHAT: Ring in an item with an extra field, and read the saved item back
        // WHY: The frontend sends whole objects it was given, so a request can carry
        //      fields the backend has no use for. Today they are ignored. A library
        //      default that refused them would turn working screens into 400s.

        mockMvc.perform(json(post("/api/order-items").with(sarah),
                        "{\"orderId\":9001,\"menuItemId\":9001,\"quantity\":1,\"modifierIds\":[9001],"
                                + "\"ringInPosition\":2,\"specialInstructions\":\"extra crispy\","
                                + "\"price\":0.01,\"somethingTheBackendHasNeverHeardOf\":true}"))
                .andExpect(status().isCreated())
                // The answer is the saved item: these fields, and the price from the menu (16.00 + 0.00), not the 0.01 that was sent
                .andExpect(jsonPath("$.keys()", containsInAnyOrder("orderItemId", "orderId", "menuItemId", "quantity", "price",
                        "specialInstructions", "status", "isLocked", "delaySeconds", "delayExpiresAt", "firedAt", "completedAt",
                        "createdAt", "sentAt", "ringInPosition")))
                .andExpect(jsonPath("$.price").value(16.00))
                .andExpect(jsonPath("$.status").value("draft"))
                .andExpect(jsonPath("$.specialInstructions").value("extra crispy"))
                .andExpect(jsonPath("$.createdAt", matchesPattern(LOCAL_DATE_TIME)));
    }
}
