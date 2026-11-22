package com.notapos.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.notapos.entity.MenuItem;
import com.notapos.entity.RestaurantTable;
import com.notapos.entity.Staff;
import com.notapos.enums.StaffRole;
import com.notapos.repository.ChallengeRepository;
import com.notapos.repository.MenuItemRepository;
import com.notapos.repository.OrderItemModifierRepository;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.PaymentRepository;
import com.notapos.repository.StaffRepository;
import com.notapos.repository.TableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for challenges on real data (step N5 of the nothing-fake
 * brief, the owner's decision D6).
 *
 * WHAT THESE HOLD THE BACKEND TO, with real requests against a real database:
 *
 *   - A person's progress is what their real, PAID checks say. Checks are
 *     opened, rung in, sent, and paid through the endpoints the screens use,
 *     and the challenge's numbers must follow: nothing is stored for them to
 *     drift from.
 *   - A challenge counts a check as the leaderboard does (closed in the period,
 *     belongs to whoever opened it).
 *   - Only a manager runs challenges, and a prize can only be marked for
 *     someone who has completed one, whatever a request says.
 *   - A challenge with a prize handed over is a record: no change, no delete.
 *
 * WHY HERE AND NOT ONLY IN ChallengeServiceTest: that test fakes the sales
 * report. Only here do the challenge, the report, the items query, and the
 * checks meet.
 *
 * @author CJ
 */
class ChallengesIntegrationTest extends BaseIntegrationTest {

    @Autowired private TableRepository tableRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderItemModifierRepository orderItemModifierRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ChallengeRepository challengeRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    // The way a screen writes a moment: 2026-11-18T17:00:00, the restaurant's clock, no zone
    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final int ROUNDS = 20;       // How many times the race at the end is run

    // The people, as rows in the staff table, and each one's sign-in
    private Staff sarahRow, danRow, kitRow, morganRow, alexRow;
    private RequestPostProcessor sarah;         // A server
    private RequestPostProcessor dan;           // A bartender
    private RequestPostProcessor kit;           // The kitchen
    private RequestPostProcessor morgan;        // A manager
    private RequestPostProcessor alex;          // Another manager

    private MenuItem burger;                    // Savory, 20.00
    private MenuItem pie;                       // Sweet, 6.00
    private MenuItem cake;                      // Sweet, 8.00
    private MenuItem soda;                      // Beverage, 3.50

    private int tablesMade = 0;

    @BeforeEach
    void setUp() {
        // Clean every table these tests touch (nothing rolls back by itself here)
        jdbc.update("DELETE FROM challenge_awards");
        challengeRepository.deleteAll();
        orderItemModifierRepository.deleteAll();
        orderItemRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        menuItemRepository.deleteAll();
        tableRepository.deleteAll();
        jdbc.update("DELETE FROM staff_sessions");
        staffRepository.deleteAll();

        sarahRow = person("Sarah", StaffRole.SERVER);
        danRow = person("Dan", StaffRole.BARTENDER);
        kitRow = person("Kit", StaffRole.KITCHEN);
        morganRow = person("Morgan", StaffRole.MANAGER);
        alexRow = person("Alex", StaffRole.MANAGER);
        sarah = signIn.as(sarahRow.getStaffId(), "Sarah", StaffRole.SERVER);
        dan = signIn.as(danRow.getStaffId(), "Dan", StaffRole.BARTENDER);
        kit = signIn.as(kitRow.getStaffId(), "Kit", StaffRole.KITCHEN);
        morgan = signIn.as(morganRow.getStaffId(), "Morgan", StaffRole.MANAGER);
        alex = signIn.as(alexRow.getStaffId(), "Alex", StaffRole.MANAGER);

        burger = menuItem("Burger", "20.00", "Savory");
        pie = menuItem("Pie", "6.00", "Sweet");
        cake = menuItem("Cake", "8.00", "Sweet");
        soda = menuItem("Soda", "3.50", "Beverage");
    }

    // ------------------------------------------------------------------
    // Helpers: the restaurant
    // ------------------------------------------------------------------

    private Staff person(String name, StaffRole role) {
        Staff staff = new Staff();
        staff.setName(name);
        staff.setRole(role.getValue());
        staff.setPinHash("not-a-real-hash-" + name);        // Nobody signs in with a PIN in these tests
        staff.setIsActive(true);
        return staffRepository.save(staff);
    }

    private MenuItem menuItem(String name, String price, String category) {
        MenuItem item = new MenuItem();
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        item.setCategory(category);
        item.setIsActive(true);
        return menuItemRepository.save(item);
    }

    private RestaurantTable newTable() {
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber("T" + (++tablesMade));
        table.setSeatCount(2);
        table.setStatus("available");
        table.setIsQuickOrder(false);
        return tableRepository.save(table);
    }

    private JsonNode read(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    // One line of a check: a menu item and how many
    private record Line(MenuItem item, int quantity) {
    }

    private Line of(MenuItem item, int quantity) {
        return new Line(item, quantity);
    }

    // One person opens a check on a new table, rings the lines in, and sends them. Open, not yet paid.
    private long openAndSend(RequestPostProcessor openedBy, Line... lines) throws Exception {
        MvcResult opened = mockMvc.perform(post("/api/orders").with(openedBy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":" + newTable().getTableId() + ",\"orderType\":\"dine_in\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        long orderId = read(opened).get("orderId").asLong();

        for (int position = 0; position < lines.length; position++) {
            mockMvc.perform(post("/api/order-items").with(openedBy)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":" + orderId + ",\"menuItemId\":" + lines[position].item().getMenuItemId()
                                    + ",\"quantity\":" + lines[position].quantity() + ",\"modifierIds\":[],\"ringInPosition\":" + position + "}"))
                    .andExpect(status().isCreated());
        }
        mockMvc.perform(post("/api/order-items/order/" + orderId + "/send-now").with(openedBy))
                .andExpect(status().isOk());
        return orderId;
    }

    // The check is paid: exactly what the backend says is due, plus a tip no sale here equals
    private void pay(long orderId, RequestPostProcessor paidBy) throws Exception {
        MvcResult check = mockMvc.perform(get("/api/orders/" + orderId + "/check").with(paidBy))
                .andExpect(status().isOk())
                .andReturn();
        String due = read(check).get("total").decimalValue().toPlainString();

        mockMvc.perform(post("/api/payments").with(paidBy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"orderId\":" + orderId + ",\"amount\":" + due + ",\"tipAmount\":77.77,\"paymentMethod\":\"credit_card\"}"))
                .andExpect(status().isCreated());
    }

    // A whole check, opened and paid by one person
    private long sell(RequestPostProcessor who, Line... lines) throws Exception {
        long orderId = openAndSend(who, lines);
        pay(orderId, who);
        return orderId;
    }

    // ------------------------------------------------------------------
    // Helpers: challenges
    // ------------------------------------------------------------------

    private String hoursFromNow(long hours) {
        return LocalDateTime.now().plusHours(hours).format(LOCAL);
    }

    // A challenge's JSON. "extra" is the part only some measures have, e.g. ",\"menuCategory\":\"Sweet\"".
    private String challengeJson(String name, String measure, String target, String extra, String startsAt, String endsAt) {
        return "{\"name\":\"" + name + "\",\"measure\":\"" + measure + "\",\"target\":" + target + extra
                + ",\"startsAt\":\"" + startsAt + "\",\"endsAt\":\"" + endsAt + "\",\"prize\":\"$20\"}";
    }

    // A manager makes a challenge that started an hour ago and runs for another day. Returns its ID.
    private long running(String measure, String target, String extra) throws Exception {
        MvcResult made = mockMvc.perform(post("/api/challenges").with(morgan)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(challengeJson("A challenge", measure, target, extra, hoursFromNow(-1), hoursFromNow(24))))
                .andExpect(status().isCreated())
                .andReturn();
        return read(made).get("challengeId").asLong();
    }

    // The whole list as a given person is sent it
    private JsonNode challengesAs(RequestPostProcessor who) throws Exception {
        return read(mockMvc.perform(get("/api/challenges").with(who)).andExpect(status().isOk()).andReturn());
    }

    // One challenge from the list (as a server reads it), or a failure saying it is not there
    private JsonNode challenge(long challengeId) throws Exception {
        for (JsonNode challenge : challengesAs(sarah)) {
            if (challenge.get("challengeId").asLong() == challengeId) {
                return challenge;
            }
        }
        return fail("Challenge " + challengeId + " is not on the list");
    }

    private boolean listed(long challengeId) throws Exception {
        for (JsonNode challenge : challengesAs(sarah)) {
            if (challenge.get("challengeId").asLong() == challengeId) {
                return true;
            }
        }
        return false;
    }

    // One person's line in a challenge, or null if they are not in it
    private JsonNode in(long challengeId, Staff who) throws Exception {
        for (JsonNode participant : challenge(challengeId).get("participants")) {
            if (participant.get("staffId").asLong() == who.getStaffId()) {
                return participant;
            }
        }
        return null;
    }

    // Money and counts are compared as amounts: 40.0 and 40.00 are the same
    private void assertProgress(String expected, JsonNode participant) {
        assertNotNull(participant, "that person is not in the challenge at all");
        assertEquals(0, new BigDecimal(expected).compareTo(participant.get("progress").decimalValue()),
                "expected progress " + expected + " but the answer holds " + participant.get("progress"));
    }

    private int award(long challengeId, Staff winner, RequestPostProcessor by) throws Exception {
        return mockMvc.perform(post("/api/challenges/" + challengeId + "/awards/" + winner.getStaffId()).with(by))
                .andReturn().getResponse().getStatus();
    }

    // ------------------------------------------------------------------
    // Progress comes from real, paid checks
    // ------------------------------------------------------------------

    @Test
    void testTotalSales_MovesWhenACheckIsPaid_NotWhenItIsRungIn() throws Exception {
        // WHAT: Test a "reach $40 in sales" challenge as a check is rung in, sent, and then paid
        // WHY: Progress is worked out from closed checks, the rule Financials and the
        //      leaderboard follow. An open check is not a sale yet for any of them.

        long challengeId = running("total_sales", "40", "");

        // Rung in and sent to the kitchen, not paid: nobody is in the challenge yet
        long open = openAndSend(sarah, of(burger, 2));
        assertNull(in(challengeId, sarahRow));

        // Paid: $40.00 before tax, exactly the target
        pay(open, sarah);
        JsonNode sarahs = in(challengeId, sarahRow);
        assertProgress("40.00", sarahs);
        assertEquals(1, sarahs.get("checksClosed").asInt());
        assertTrue(sarahs.get("completed").asBoolean());

        // Dan sells less and is in the challenge, short of the target
        sell(dan, of(burger, 1), of(soda, 1));
        assertProgress("23.50", in(challengeId, danRow));
        assertFalse(in(challengeId, danRow).get("completed").asBoolean());
    }

    @Test
    void testAChallengesProgress_IsThatPersonsLineOnTheLeaderboardForTheSamePeriod() throws Exception {
        // WHAT: Compare a total-sales challenge with the leaderboard for the challenge's own period
        // WHY: One counting rule. If the two ever counted a check differently, a server
        //      would see one number on the leaderboard and another on the challenge.

        String startsAt = hoursFromNow(-1), endsAt = hoursFromNow(24);
        long challengeId = read(mockMvc.perform(post("/api/challenges").with(morgan).contentType(MediaType.APPLICATION_JSON)
                        .content(challengeJson("Same numbers", "total_sales", "1000", "", startsAt, endsAt)))
                .andExpect(status().isCreated()).andReturn()).get("challengeId").asLong();
        sell(sarah, of(burger, 2), of(pie, 1));
        sell(sarah, of(soda, 3));
        long takenOver = openAndSend(dan, of(cake, 2));            // Dan opens it, Sarah takes the payment: Dan's sale
        pay(takenOver, sarah);

        JsonNode leaderboard = read(mockMvc.perform(get("/api/reports/staff-sales").with(sarah)
                        .param("from", startsAt).param("to", endsAt))
                .andExpect(status().isOk()).andReturn()).get("staff");

        assertEquals(2, leaderboard.size());
        for (JsonNode row : leaderboard) {
            Staff who = row.get("staffId").asLong() == sarahRow.getStaffId() ? sarahRow : danRow;
            assertEquals(0, row.get("sales").decimalValue().compareTo(in(challengeId, who).get("progress").decimalValue()),
                    who.getName() + ": leaderboard " + row.get("sales") + ", challenge " + in(challengeId, who).get("progress"));
        }
        assertProgress("56.50", in(challengeId, sarahRow));
        assertProgress("16.00", in(challengeId, danRow));
    }

    @Test
    void testItemsSold_InACategory_CountsThatCategoryOnly_ByQuantity() throws Exception {
        // WHAT: Test a "sell 5 from Sweet" challenge
        // WHY: Pie and cake are Sweet; a burger and a soda on the same check are not.
        //      Three pies on one line are three items.

        long challengeId = running("items_sold", "5", ",\"menuCategory\":\"Sweet\"");

        sell(sarah, of(pie, 3), of(burger, 2), of(soda, 4));
        assertProgress("3", in(challengeId, sarahRow));
        assertFalse(in(challengeId, sarahRow).get("completed").asBoolean());

        sell(sarah, of(cake, 2));
        assertProgress("5", in(challengeId, sarahRow));
        assertTrue(in(challengeId, sarahRow).get("completed").asBoolean());

        // Dan sold checks but nothing sweet: he is in the challenge, at zero
        sell(dan, of(burger, 1));
        assertProgress("0", in(challengeId, danRow));
        assertEquals("Sweet", challenge(challengeId).get("menuCategory").asString());
    }

    @Test
    void testItemsSold_OfOneItem_KeepsCountingAfterTheItemIs86dAndTakenOffTheMenu() throws Exception {
        // WHAT: Test a "sell 3 pies" challenge whose pie is then 86'd and deactivated
        // WHY: The pies were sold. What happens to the menu afterwards does not unsell them.

        long challengeId = running("items_sold", "3", ",\"menuItemId\":" + pie.getMenuItemId());
        sell(sarah, of(pie, 3), of(cake, 5));

        mockMvc.perform(put("/api/menu-items/" + pie.getMenuItemId() + "/86").with(morgan)).andExpect(status().isOk());
        pie = menuItemRepository.findById(pie.getMenuItemId()).orElseThrow();
        pie.setIsActive(false);
        menuItemRepository.save(pie);

        JsonNode challenge = challenge(challengeId);
        assertEquals("Pie", challenge.get("menuItemName").asString());
        assertProgress("3", in(challengeId, sarahRow));                // The cake is not a pie
        assertTrue(in(challengeId, sarahRow).get("completed").asBoolean());
    }

    @Test
    void testItemsSold_InACategoryNoItemBelongsToAnyMore_ShowsZero() throws Exception {
        // WHAT: Test a category challenge after every item has been moved out of the category
        // WHY: The brief's own rule. The category is the one an item has today.

        long challengeId = running("items_sold", "5", ",\"menuCategory\":\"Sweet\"");
        sell(sarah, of(pie, 2), of(cake, 1));
        assertProgress("3", in(challengeId, sarahRow));

        for (MenuItem item : List.of(pie, cake)) {
            item.setCategory("Desserts");
            menuItemRepository.save(item);
        }

        assertProgress("0", in(challengeId, sarahRow));
    }

    @Test
    void testBiggestCheck_IsOneCheck_NotManyAddedUp() throws Exception {
        // WHAT: Test a "close one check of at least $60" challenge
        // WHY: Sarah's three checks add up to more than $60, and none of them is $60

        long challengeId = running("biggest_check", "60", "");

        sell(sarah, of(burger, 2));
        sell(sarah, of(burger, 2));
        sell(sarah, of(burger, 1));
        assertProgress("40.00", in(challengeId, sarahRow));
        assertFalse(in(challengeId, sarahRow).get("completed").asBoolean());

        sell(dan, of(burger, 3));
        assertProgress("60.00", in(challengeId, danRow));
        assertTrue(in(challengeId, danRow).get("completed").asBoolean());
    }

    @Test
    void testCheckAverage_IsOnTrackWhileItRuns_AndCompletedOnlyOnceItHasEnded() throws Exception {
        // WHAT: Test a "hold a $30 average over 2 checks" challenge, running and then ended
        // WHY: An average can fall with the next small check, so nobody has completed it
        //      while it runs, and no prize can be marked. Ending it settles it.

        long challengeId = running("check_average", "30", ",\"minChecks\":2");

        // One big check: a high average, but one check is below the minimum of two
        sell(sarah, of(burger, 3));
        assertProgress("60.00", in(challengeId, sarahRow));
        assertFalse(in(challengeId, sarahRow).get("meetsTarget").asBoolean());

        // A second check: ($60 + $20) / 2 = $40, with enough checks. On track, not completed.
        sell(sarah, of(burger, 1));
        assertProgress("40.00", in(challengeId, sarahRow));
        assertTrue(in(challengeId, sarahRow).get("meetsTarget").asBoolean());
        assertFalse(in(challengeId, sarahRow).get("completed").asBoolean());
        assertEquals(409, award(challengeId, sarahRow, morgan));

        // Dan has two checks averaging $11.75: below
        sell(dan, of(burger, 1));
        sell(dan, of(soda, 1));

        // The manager ends it
        mockMvc.perform(post("/api/challenges/" + challengeId + "/end").with(morgan)).andExpect(status().isOk());

        assertEquals("finished", challenge(challengeId).get("state").asString());
        assertTrue(in(challengeId, sarahRow).get("completed").asBoolean());
        assertFalse(in(challengeId, danRow).get("completed").asBoolean());
        assertEquals(200, award(challengeId, sarahRow, morgan));
    }

    // ------------------------------------------------------------------
    // The period
    // ------------------------------------------------------------------

    @Test
    void testAChallenge_CountsOnlyChecksClosedInsideItsPeriod() throws Exception {
        // WHAT: Test a check closed before the challenge started, and one closed after it was ended
        // WHY: "Inside its period". Ending early must stop the count at that moment.

        long challengeId = running("total_sales", "1000", "");

        long before = sell(sarah, of(burger, 5));
        jdbc.update("UPDATE orders SET created_at = created_at - INTERVAL '3 hours', closed_at = closed_at - INTERVAL '2 hours' WHERE order_id = ?", before);
        sell(sarah, of(burger, 1));
        assertProgress("20.00", in(challengeId, sarahRow));

        mockMvc.perform(post("/api/challenges/" + challengeId + "/end").with(morgan)).andExpect(status().isOk());
        sell(sarah, of(burger, 2));                                 // After the end: not counted

        assertProgress("20.00", in(challengeId, sarahRow));
        assertEquals("finished", challenge(challengeId).get("state").asString());
    }

    @Test
    void testAChallengeThatHasNotStarted_HasNobodyInIt_AndCannotBeEnded() throws Exception {
        // WHAT: Test a challenge that starts tomorrow, with sales happening today
        // WHY: It is listed as coming, counts nothing yet, and END makes no sense for it

        sell(sarah, of(burger, 5));
        long challengeId = read(mockMvc.perform(post("/api/challenges").with(morgan).contentType(MediaType.APPLICATION_JSON)
                        .content(challengeJson("Tomorrow", "total_sales", "10", "", hoursFromNow(24), hoursFromNow(48))))
                .andExpect(status().isCreated()).andReturn()).get("challengeId").asLong();

        assertEquals("upcoming", challenge(challengeId).get("state").asString());
        assertEquals(0, challenge(challengeId).get("participants").size());
        mockMvc.perform(post("/api/challenges/" + challengeId + "/end").with(morgan)).andExpect(status().isConflict());
    }

    @Test
    void testAChallengesTimes_ComeBackAsTheyWereTyped() throws Exception {
        // WHAT: Test the start and end in the answer against what the manager entered
        // WHY: Typed on the restaurant's clock, stored as moments, sent back on the
        //      restaurant's clock. The round trip must not shift them by a time zone.

        MvcResult made = mockMvc.perform(post("/api/challenges").with(morgan).contentType(MediaType.APPLICATION_JSON)
                        .content(challengeJson("Friday dinner", "total_sales", "500", "", "2026-11-20T17:00:00", "2026-11-20T23:30:00")))
                .andExpect(status().isCreated()).andReturn();

        assertEquals("2026-11-20T17:00:00", read(made).get("startsAt").asString());
        assertEquals("2026-11-20T23:30:00", read(made).get("endsAt").asString());
    }

    // ------------------------------------------------------------------
    // Prizes
    // ------------------------------------------------------------------

    @Test
    void testAManager_MarksThePrizeOfSomeoneWhoCompletedIt_AndItIsKept() throws Exception {
        // WHAT: Test AWARDED for a person who reached the target
        // WHY: The record says who marked it. A second marking, by another manager,
        //      changes nothing: the first manager's name stays.

        long challengeId = running("total_sales", "40", "");
        sell(sarah, of(burger, 2));
        assertNull(in(challengeId, sarahRow).get("awardedByName").asStringOpt().orElse(null));

        assertEquals(200, award(challengeId, sarahRow, morgan));
        assertEquals("Morgan", in(challengeId, sarahRow).get("awardedByName").asString());
        String firstMarkedAt = in(challengeId, sarahRow).get("awardedAt").asString();

        assertEquals(200, award(challengeId, sarahRow, alex));
        assertEquals("Morgan", in(challengeId, sarahRow).get("awardedByName").asString());
        assertEquals(firstMarkedAt, in(challengeId, sarahRow).get("awardedAt").asString());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM challenge_awards", Integer.class));
    }

    @Test
    void testAPrize_CannotBeMarkedForSomeoneWhoHasNotCompletedIt_WhateverTheRequestSays() throws Exception {
        // WHAT: Test AWARDED for someone short of the target, for someone with no checks, and from a server
        // WHY: The backend checks the checks. It does not trust a screen to offer the
        //      button only to the right people.

        long challengeId = running("total_sales", "40", "");
        sell(dan, of(burger, 1));                                   // $20 of $40

        MvcResult short_ = mockMvc.perform(post("/api/challenges/" + challengeId + "/awards/" + danRow.getStaffId()).with(morgan))
                .andExpect(status().isConflict()).andReturn();
        assertEquals("Dan has not completed this challenge.", read(short_).get("message").asString());
        assertEquals(409, award(challengeId, sarahRow, morgan));    // Sarah has no checks at all
        assertEquals(409, mockMvc.perform(post("/api/challenges/" + challengeId + "/awards/987654").with(morgan))
                .andReturn().getResponse().getStatus());            // Nobody has that ID

        // A server may not mark a prize, not even her own after completing it
        sell(sarah, of(burger, 2));
        assertEquals(403, award(challengeId, sarahRow, sarah));

        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM challenge_awards", Integer.class));
    }

    @Test
    void testOnceAPrizeIsHandedOver_TheChallengeIsARecord_ItCannotBeChangedOrDeleted() throws Exception {
        // WHAT: Test PUT and DELETE on a challenge that has an award
        // WHY: Raising the target afterwards would leave a prize with someone who, by
        //      the new rules, never earned it; deleting it would destroy the only record
        //      that the prize was given

        long challengeId = running("total_sales", "40", "");
        sell(sarah, of(burger, 2));
        assertEquals(200, award(challengeId, sarahRow, morgan));

        mockMvc.perform(put("/api/challenges/" + challengeId).with(morgan).contentType(MediaType.APPLICATION_JSON)
                        .content(challengeJson("Harder now", "total_sales", "4000", "", hoursFromNow(-1), hoursFromNow(24))))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/challenges/" + challengeId).with(morgan)).andExpect(status().isConflict());

        JsonNode still = challenge(challengeId);
        assertEquals("A challenge", still.get("name").asString());
        assertEquals(0, new BigDecimal("40").compareTo(still.get("target").decimalValue()));
        assertEquals("Morgan", in(challengeId, sarahRow).get("awardedByName").asString());

        // It can still be ended: that changes nobody's result
        mockMvc.perform(post("/api/challenges/" + challengeId + "/end").with(morgan)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // What a manager does with a challenge
    // ------------------------------------------------------------------

    @Test
    void testAManager_ChangesAChallenge_AndProgressFollowsTheNewRules() throws Exception {
        // WHAT: Test changing a challenge from total sales to items of a category
        // WHY: Nothing was stored for the old rules, so there is nothing to migrate:
        //      the same checks are simply counted the new way

        long challengeId = running("total_sales", "500", "");
        sell(sarah, of(pie, 2), of(burger, 1));
        assertProgress("32.00", in(challengeId, sarahRow));

        mockMvc.perform(put("/api/challenges/" + challengeId).with(alex).contentType(MediaType.APPLICATION_JSON)
                        .content(challengeJson("Sweet tooth", "items_sold", "2", ",\"menuCategory\":\"Sweet\"", hoursFromNow(-1), hoursFromNow(24))))
                .andExpect(status().isOk());

        JsonNode changed = challenge(challengeId);
        assertEquals("Sweet tooth", changed.get("name").asString());
        assertEquals("items_sold", changed.get("measure").asString());
        assertEquals("Morgan", changed.get("createdByName").asString());           // Still made by Morgan, though Alex edited it
        assertProgress("2", in(challengeId, sarahRow));
        assertTrue(in(challengeId, sarahRow).get("completed").asBoolean());
    }

    @Test
    void testAManager_DeletesAChallengeNobodyWasAwardedFor() throws Exception {
        long challengeId = running("total_sales", "500", "");
        sell(sarah, of(burger, 1));

        mockMvc.perform(delete("/api/challenges/" + challengeId).with(morgan)).andExpect(status().isNoContent());

        assertFalse(listed(challengeId));
        mockMvc.perform(delete("/api/challenges/" + challengeId).with(morgan)).andExpect(status().isNotFound());
    }

    @Test
    void testWhoMadeAChallenge_IsTheSignedInManager_WhateverTheRequestClaims() throws Exception {
        // WHAT: Test a request that names another creator and an ID of its own
        // WHY: A create endpoint builds a new row. A request carrying an ID must not
        //      become "replace that challenge", and nobody can sign someone else's name.

        long existing = running("total_sales", "500", "");

        MvcResult made = mockMvc.perform(post("/api/challenges").with(alex).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"challengeId\":" + existing + ",\"createdByName\":\"Sarah\",\"createdByStaffId\":" + sarahRow.getStaffId()
                                + ",\"state\":\"finished\",\"name\":\"Mine\",\"measure\":\"biggest_check\",\"target\":90,"
                                + "\"startsAt\":\"" + hoursFromNow(-1) + "\",\"endsAt\":\"" + hoursFromNow(5) + "\"}"))
                .andExpect(status().isCreated()).andReturn();

        long newId = read(made).get("challengeId").asLong();
        assertNotEquals(existing, newId);
        assertEquals("Alex", read(made).get("createdByName").asString());
        assertEquals("active", read(made).get("state").asString());
        assertEquals("A challenge", challenge(existing).get("name").asString());   // The existing one is untouched
        assertEquals(2, challengesAs(sarah).size());
    }

    @Test
    void testAChallengeThatDoesNotAddUp_IsRefusedWithWordsForTheForm() throws Exception {
        // WHAT: Test the rules across fields, through the real endpoint and the real menu
        // WHY: The categories and items a challenge may count come from the menu, not a list in the code

        String start = hoursFromNow(-1), end = hoursFromNow(5);
        String[][] cases = {
                {challengeJson("x", "items_sold", "5", ",\"menuCategory\":\"Dinosaurs\"", start, end), "No menu item is in the category \"Dinosaurs\"."},
                {challengeJson("x", "items_sold", "5", ",\"menuItemId\":987654", start, end), "That menu item does not exist."},
                {challengeJson("x", "items_sold", "5", ",\"menuCategory\":\"Sweet\",\"menuItemId\":" + pie.getMenuItemId(), start, end), "Pick a menu category or one item, not both."},
                {challengeJson("x", "items_sold", "5", "", start, end), "Pick the menu category or the item this challenge counts."},
                {challengeJson("x", "items_sold", "2.5", ",\"menuCategory\":\"Sweet\"", start, end), "A number of items has to be a whole number."},
                {challengeJson("x", "check_average", "30", "", start, end), "Say how many checks a person needs before their average counts."},
                {challengeJson("x", "fewest_mistakes", "30", "", start, end), "Pick what the challenge measures."},
                {challengeJson("x", "total_sales", "30", "", end, start), "The end has to be after the start."},
        };
        for (String[] oneCase : cases) {
            MvcResult refused = mockMvc.perform(post("/api/challenges").with(morgan).contentType(MediaType.APPLICATION_JSON).content(oneCase[0]))
                    .andExpect(status().isBadRequest()).andReturn();
            assertEquals(oneCase[1], read(refused).get("message").asString());
        }
        assertEquals(0, challengeRepository.count());

        // And a category the menu does have is accepted, even when its only items are off the menu today
        for (MenuItem item : List.of(pie, cake)) {
            item.setIsActive(false);
            menuItemRepository.save(item);
        }
        mockMvc.perform(post("/api/challenges").with(morgan).contentType(MediaType.APPLICATION_JSON)
                        .content(challengeJson("x", "items_sold", "5", ",\"menuCategory\":\"Sweet\"", start, end)))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // Who may do what, and what is not in the answer
    // ------------------------------------------------------------------

    @Test
    void testEveryRole_ReadsTheSameChallengesWithEveryonesProgress_AndNoTips() throws Exception {
        // WHAT: Read the raw answer as a server, the kitchen, and a manager
        // WHY: The owner's decision D6: everyone sees everyone's progress. And tips are
        //      private: every check here was tipped 77.77, an amount no sale equals.

        long challengeId = running("total_sales", "40", "");
        sell(sarah, of(burger, 2));
        sell(dan, of(burger, 1));
        award(challengeId, sarahRow, morgan);

        String asServer = mockMvc.perform(get("/api/challenges").with(sarah)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String asKitchen = mockMvc.perform(get("/api/challenges").with(kit)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String asManager = mockMvc.perform(get("/api/challenges").with(morgan)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertEquals(asServer, asKitchen);
        assertEquals(asServer, asManager);
        assertEquals(2, objectMapper.readTree(asServer).get(0).get("participants").size());
        assertFalse(asServer.toLowerCase().contains("tip"), "the answer mentions a tip: " + asServer);
        assertFalse(asServer.contains("77.77"), "the answer holds a tip's amount: " + asServer);
    }

    @Test
    void testOnlyAManager_RunsChallenges_AndNobodyReadsThemWithoutSigningIn() throws Exception {
        // WHAT: Test every change as a bartender, and reading with no sign-in
        // WHY: A bartender may 86 an item and write to the floor; challenges are not theirs to run

        long challengeId = running("total_sales", "40", "");
        String json = challengeJson("Mine now", "total_sales", "1", "", hoursFromNow(-1), hoursFromNow(5));

        mockMvc.perform(post("/api/challenges").with(dan).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
        mockMvc.perform(put("/api/challenges/" + challengeId).with(dan).contentType(MediaType.APPLICATION_JSON).content(json)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/challenges/" + challengeId + "/end").with(dan)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/challenges/" + challengeId).with(dan)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/challenges")).andExpect(status().isUnauthorized());

        JsonNode untouched = challenge(challengeId);
        assertEquals("A challenge", untouched.get("name").asString());
        assertEquals("active", untouched.get("state").asString());
        assertEquals(1, challengeRepository.count());
    }

    @Test
    void testWithNoChallenges_TheListIsEmpty() throws Exception {
        // WHY: "No challenges yet." comes from an empty list, not a 404

        assertEquals("[]", mockMvc.perform(get("/api/challenges").with(sarah)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    // ------------------------------------------------------------------
    // Two managers at once
    // ------------------------------------------------------------------

    // Run two requests at the same instant and return the HTTP status each one got.
    // A CyclicBarrier is a starting gate: each thread waits at it until both have
    // arrived, then both are released together (see TwoTerminalsIntegrationTest).
    private List<Integer> atTheSameMoment(Callable<Integer> first, Callable<Integer> second) throws Exception {
        ExecutorService terminals = Executors.newFixedThreadPool(2);
        CyclicBarrier startingGate = new CyclicBarrier(2);
        try {
            List<Future<Integer>> answers = new ArrayList<>();
            for (Callable<Integer> request : List.of(first, second)) {
                answers.add(terminals.submit(() -> {
                    startingGate.await(5, TimeUnit.SECONDS);
                    return request.call();
                }));
            }
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> answer : answers) {
                statuses.add(answer.get(20, TimeUnit.SECONDS));
            }
            return statuses;
        } finally {
            terminals.shutdownNow();
        }
    }

    @Test
    void testOneManagerDeletes_WhileAnotherMarksThePrize_OneOfThemWins_AndNothingIsLeftHalfDone() throws Exception {
        // WHAT: DELETE and AWARDED for the same challenge, sent at the same instant, twenty times over
        // WHY: "No prize on it, so it may be deleted" is two steps, and the other
        //      manager's AWARDED can land between them. Each request holds the challenge's
        //      row while it works, so one goes first and the other sees what it did:
        //      either the challenge is gone and the award was refused (404), or the award
        //      stands and the delete was refused (409). Never a server error, and never
        //      an award for a challenge that no longer exists.

        for (int round = 0; round < ROUNDS; round++) {
            long challengeId = running("total_sales", "20", "");
            if (round == 0) {
                sell(sarah, of(burger, 1));                         // Sarah has completed every round's challenge: they share one period
            }

            List<Integer> statuses = atTheSameMoment(
                    () -> mockMvc.perform(delete("/api/challenges/" + challengeId).with(morgan)).andReturn().getResponse().getStatus(),
                    () -> award(challengeId, sarahRow, alex));

            int deleted = statuses.get(0), awarded = statuses.get(1);
            boolean stillThere = challengeRepository.existsById(challengeId);
            int awards = jdbc.queryForObject("SELECT count(*) FROM challenge_awards WHERE challenge_id = ?", Integer.class, challengeId);
            String outcome = "round " + round + ": delete " + deleted + ", award " + awarded + ", challenge still there: " + stillThere + ", awards: " + awards;

            if (deleted == 204) {
                assertEquals(404, awarded, outcome);
                assertFalse(stillThere, outcome);
                assertEquals(0, awards, outcome);
            } else {
                assertEquals(409, deleted, outcome);
                assertEquals(200, awarded, outcome);
                assertTrue(stillThere, outcome);
                assertEquals(1, awards, outcome);
            }
        }
    }
}
