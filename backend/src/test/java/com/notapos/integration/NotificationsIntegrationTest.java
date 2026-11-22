package com.notapos.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.notapos.entity.MenuItem;
import com.notapos.entity.RestaurantTable;
import com.notapos.enums.StaffRole;
import com.notapos.repository.MenuItemRepository;
import com.notapos.repository.NotificationRepository;
import com.notapos.repository.OrderItemModifierRepository;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.PaymentRepository;
import com.notapos.repository.TableRepository;
import com.notapos.security.StaffSession;
import com.notapos.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.IllegalTransactionStateException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for notifications (step N3 of
 * Documentation/nothing-fake-brief.md, the owner's decision D5): the bell in the
 * top bar, through real requests against a real database.
 *
 * THE RULES:
 *   - An item is 86'd or put back on: everyone is told, except whoever did it.
 *   - Order up (nothing sent on a check is left to make): the person who opened
 *     the check is told, and nobody else.
 *   - A message from the kitchen, a bartender, or a manager: everyone but the writer.
 *   - A person reads and dismisses ONLY THEIR OWN. One person dismissing changes
 *     nothing for anyone else, and a dismissed notification stays dismissed after
 *     signing out and in again, on any terminal.
 *
 * WHY HERE: each of these is about what several people are and are not sent
 * after one of them does something. Only real requests, as real people, against
 * one database show that.
 *
 * @author CJ
 */
class NotificationsIntegrationTest extends BaseIntegrationTest {

    @Autowired private TableRepository tableRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderItemModifierRepository orderItemModifierRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationService notificationService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper objectMapper;

    // The people. Each is one sign-in, used for every request that person makes.
    private RequestPostProcessor kit;           // The kitchen
    private RequestPostProcessor sarah;         // A server
    private RequestPostProcessor dan;           // A bartender
    private RequestPostProcessor harper;        // A host

    private MenuItem burger;
    private MenuItem gardenToast;
    private RestaurantTable w5;

    @BeforeEach
    void setUp() {
        // Clean every table these tests touch (nothing rolls back by itself here)
        jdbc.update("DELETE FROM notification_dismissals");
        notificationRepository.deleteAll();
        orderItemModifierRepository.deleteAll();
        orderItemRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        menuItemRepository.deleteAll();
        tableRepository.deleteAll();

        kit = signIn.as(9L, "Kit", StaffRole.KITCHEN);
        sarah = signIn.as(7L, "Sarah", StaffRole.SERVER);
        dan = signIn.as(8L, "Dan", StaffRole.BARTENDER);
        harper = signIn.as(4L, "Harper", StaffRole.HOST);

        burger = menuItem("Burger");
        gardenToast = menuItem("Garden Toast");
        w5 = table("W5", false);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private MenuItem menuItem(String name) {
        MenuItem item = new MenuItem();
        item.setName(name);
        item.setPrice(new BigDecimal("20.00"));
        item.setCategory("Savory");
        item.setIsActive(true);
        return menuItemRepository.save(item);
    }

    private RestaurantTable table(String number, boolean quickOrder) {
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber(number);
        table.setSeatCount(4);
        table.setStatus("available");
        table.setIsQuickOrder(quickOrder);
        return tableRepository.save(table);
    }

    // What a person's bell holds: the raw answer of GET /api/notifications
    private JsonNode bellOf(RequestPostProcessor who) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/notifications").with(who)).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private List<String> textsOn(JsonNode bell) {
        List<String> texts = new ArrayList<>();
        for (JsonNode notification : bell) {
            texts.add(notification.get("text").asString());
        }
        return texts;
    }

    private List<String> textsFor(RequestPostProcessor who) throws Exception {
        return textsOn(bellOf(who));
    }

    private void eightySix(MenuItem item, RequestPostProcessor by) throws Exception {
        mockMvc.perform(put("/api/menu-items/" + item.getMenuItemId() + "/86").with(by)).andExpect(status().isOk());
    }

    private void putBackOn(MenuItem item, RequestPostProcessor by) throws Exception {
        mockMvc.perform(put("/api/menu-items/" + item.getMenuItemId() + "/un-86").with(by)).andExpect(status().isOk());
    }

    private void message(RequestPostProcessor from, String text) throws Exception {
        mockMvc.perform(post("/api/notifications/messages").with(from)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + text + "\"}"))
                .andExpect(status().isCreated());
    }

    // A check opened by one person with burgers rung in and sent, through the real endpoints.
    // Returns the IDs of its items, in order.
    private List<Long> checkWithBurgers(RestaurantTable table, RequestPostProcessor openedBy, int howMany) throws Exception {
        MvcResult opened = mockMvc.perform(post("/api/orders").with(openedBy)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tableId\":" + table.getTableId() + ",\"orderType\":\"dine_in\"}"))
                .andExpect(status().isCreated()).andReturn();
        long orderId = objectMapper.readTree(opened.getResponse().getContentAsString()).get("orderId").asLong();

        List<Long> itemIds = new ArrayList<>();
        for (int position = 0; position < howMany; position++) {
            MvcResult rung = mockMvc.perform(post("/api/order-items").with(openedBy)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"orderId\":" + orderId + ",\"menuItemId\":" + burger.getMenuItemId()
                                    + ",\"quantity\":1,\"modifierIds\":[],\"ringInPosition\":" + position + "}"))
                    .andExpect(status().isCreated()).andReturn();
            itemIds.add(objectMapper.readTree(rung.getResponse().getContentAsString()).get("orderItemId").asLong());
        }
        mockMvc.perform(post("/api/order-items/order/" + orderId + "/send-now").with(openedBy)).andExpect(status().isOk());
        return itemIds;
    }

    private void complete(Long itemId, RequestPostProcessor by) throws Exception {
        mockMvc.perform(put("/api/order-items/" + itemId + "/start").with(by)).andExpect(status().isOk());
        mockMvc.perform(put("/api/order-items/" + itemId + "/complete").with(by)).andExpect(status().isOk());
    }

    // ------------------------------------------------------------------
    // 86
    // ------------------------------------------------------------------

    @Test
    void testAnItemIs86d_EveryoneElseIsTold_AndThePersonWhoDidItIsNot() throws Exception {
        // WHAT: Kit 86's the Garden Toast
        // WHY: The brief's first example: "A 86's an item, B is told and A is not"
        eightySix(gardenToast, kit);

        assertEquals(List.of("Garden Toast is 86'd"), textsFor(sarah));
        assertEquals(List.of("Garden Toast is 86'd"), textsFor(dan));
        assertEquals(List.of("Garden Toast is 86'd"), textsFor(harper));
        assertEquals(List.of(), textsFor(kit));

        JsonNode notice = bellOf(sarah).get(0);
        assertEquals("item_86", notice.get("kind").asString());
        assertEquals("Kit", notice.get("fromName").asString());
        // The restaurant's own clock with no zone, like every other time in JSON, and close to now
        // (the test below this group says why it is not a moment in UTC)
        LocalDateTime created = LocalDateTime.parse(notice.get("createdAt").asString());
        assertTrue(Duration.between(created, LocalDateTime.now()).abs().getSeconds() < 60, "created " + created);
    }

    @Test
    void testAnItemIsPutBackOn_EveryoneElseIsToldThatToo_NewestFirst() throws Exception {
        eightySix(gardenToast, kit);
        putBackOn(gardenToast, dan);                            // A bartender puts it back

        assertEquals(List.of("Garden Toast is back on", "Garden Toast is 86'd"), textsFor(sarah));
        assertEquals(List.of("Garden Toast is back on"), textsFor(kit));          // Kit is told about the part Kit did not do
        assertEquals(List.of("Garden Toast is 86'd"), textsFor(dan));
        assertEquals("item_back", bellOf(sarah).get(0).get("kind").asString());
    }

    @Test
    void testEightySixingWhatIsAlreadyOut_ShouldTellNobodyAgain() throws Exception {
        // WHY: Two people tap the same tile; the floor gets one notice
        eightySix(gardenToast, kit);
        eightySix(gardenToast, dan);

        assertEquals(List.of("Garden Toast is 86'd"), textsFor(sarah));
        assertEquals(1, notificationRepository.count());
    }

    // ------------------------------------------------------------------
    // Order up
    // ------------------------------------------------------------------

    @Test
    void testOrderUp_ReachesThePersonWhoOpenedTheCheck_AndNobodyElse_WhenTheLastItemIsMade() throws Exception {
        // WHAT: Sarah's table has two burgers. Kit makes one, then the other.
        List<Long> burgers = checkWithBurgers(w5, sarah, 2);

        complete(burgers.get(0), kit);
        assertEquals(List.of(), textsFor(sarah));               // One is still to make: not up yet

        complete(burgers.get(1), kit);
        assertEquals(List.of("Table W5 is up"), textsFor(sarah));
        assertEquals("order_up", bellOf(sarah).get(0).get("kind").asString());
        assertEquals(List.of(), textsFor(dan));                 // Not Dan's table
        assertEquals(List.of(), textsFor(harper));
        assertEquals(List.of(), textsFor(kit));                 // Kit made it
    }

    @Test
    void testOrderUp_WhenTheOpenerMakesItThemselves_TellsNobody() throws Exception {
        // WHAT: Dan (a bartender may open checks and make items) completes his own table's drink
        List<Long> drinks = checkWithBurgers(table("B1", false), dan, 1);

        complete(drinks.get(0), dan);

        assertEquals(0, notificationRepository.count());
    }

    @Test
    void testOrderUp_ForAQuickOrder_NamesItAsOne() throws Exception {
        List<Long> burgers = checkWithBurgers(table("QO3", true), sarah, 1);

        complete(burgers.get(0), kit);

        assertEquals(List.of("Quick Order QO3 is up"), textsFor(sarah));
    }

    @Test
    void testCompletingTheSameItemTwice_ShouldSayOrderUpOnce() throws Exception {
        List<Long> burgers = checkWithBurgers(w5, sarah, 1);

        complete(burgers.get(0), kit);
        mockMvc.perform(put("/api/order-items/" + burgers.get(0) + "/complete").with(kit)).andExpect(status().isOk());

        assertEquals(List.of("Table W5 is up"), textsFor(sarah));
    }

    // ------------------------------------------------------------------
    // Messages
    // ------------------------------------------------------------------

    @Test
    void testAMessageFromTheKitchen_ReachesEveryoneButTheWriter() throws Exception {
        message(kit, "Fryer is down for 10 minutes");

        for (RequestPostProcessor person : List.of(sarah, dan, harper)) {
            JsonNode bell = bellOf(person);
            assertEquals(List.of("Fryer is down for 10 minutes"), textsOn(bell));
            assertEquals("message", bell.get(0).get("kind").asString());
            assertEquals("Kit", bell.get(0).get("fromName").asString());
        }
        assertEquals(List.of(), textsFor(kit));
    }

    @Test
    void testAServerOrAHost_MayNotWriteAMessage_AndNothingIsSent() throws Exception {
        for (RequestPostProcessor person : List.of(sarah, harper)) {
            mockMvc.perform(post("/api/notifications/messages").with(person)
                            .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"hello\"}"))
                    .andExpect(status().isForbidden());
        }
        assertEquals(0, notificationRepository.count());
    }

    @Test
    void testAMessage_CannotBeSentAsSomeoneElse() throws Exception {
        // WHAT: The request names another writer
        // WHY: Who wrote it is the sign-in, never the request
        mockMvc.perform(post("/api/notifications/messages").with(kit)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Free drinks\",\"fromName\":\"Morgan\",\"createdByStaffId\":2,\"staffId\":2}"))
                .andExpect(status().isCreated());

        assertEquals("Kit", bellOf(sarah).get(0).get("fromName").asString());
        assertEquals(List.of(), textsFor(kit));                  // Still Kit's own message: not shown to Kit
    }

    // ------------------------------------------------------------------
    // Reading and dismissing: only your own
    // ------------------------------------------------------------------

    @Test
    void testDismissing_ChangesNothingForAnyoneElse() throws Exception {
        // WHY: The brief's second example: "B dismissing changes nothing for C"
        eightySix(gardenToast, kit);
        long id = bellOf(sarah).get(0).get("notificationId").asLong();

        mockMvc.perform(post("/api/notifications/" + id + "/dismiss").with(sarah)).andExpect(status().isNoContent());

        assertEquals(List.of(), textsFor(sarah));
        assertEquals(List.of("Garden Toast is 86'd"), textsFor(dan));
        assertEquals(List.of("Garden Toast is 86'd"), textsFor(harper));
    }

    @Test
    void testADismissedNotification_StaysDismissedAfterSigningOutAndInAgain() throws Exception {
        // WHAT: Sarah dismisses on one terminal, then signs in afresh (as on another terminal)
        // WHY: Dismissing is kept in the database, by person, not on the device
        eightySix(gardenToast, kit);
        message(kit, "Fryer is down");
        long toast = bellOf(sarah).get(1).get("notificationId").asLong();
        mockMvc.perform(post("/api/notifications/" + toast + "/dismiss").with(sarah)).andExpect(status().isNoContent());

        RequestPostProcessor sarahOnAnotherTerminal = signIn.as(7L, "Sarah", StaffRole.SERVER);     // A new session, the same person

        assertEquals(List.of("Fryer is down"), textsFor(sarahOnAnotherTerminal));
    }

    @Test
    void testDismissingTwice_IsTheSameAsOnce() throws Exception {
        eightySix(gardenToast, kit);
        long id = bellOf(sarah).get(0).get("notificationId").asLong();

        mockMvc.perform(post("/api/notifications/" + id + "/dismiss").with(sarah)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/notifications/" + id + "/dismiss").with(sarah)).andExpect(status().isNoContent());

        assertEquals(List.of(), textsFor(sarah));
    }

    @Test
    void testAPersonCannotDismissWhatIsNotTheirs() throws Exception {
        // WHAT: Order up for Sarah. Dan, and Kit who caused it, try to dismiss it by its number.
        // WHY: "A person reads and dismisses only their own." They are told it does not
        //      exist, and Sarah still has it.
        List<Long> burgers = checkWithBurgers(w5, sarah, 1);
        complete(burgers.get(0), kit);
        long id = bellOf(sarah).get(0).get("notificationId").asLong();

        mockMvc.perform(post("/api/notifications/" + id + "/dismiss").with(dan)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/notifications/" + id + "/dismiss").with(kit)).andExpect(status().isNotFound());
        mockMvc.perform(post("/api/notifications/999999/dismiss").with(dan)).andExpect(status().isNotFound());

        assertEquals(List.of("Table W5 is up"), textsFor(sarah));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM notification_dismissals", Integer.class));
    }

    @Test
    void testDismissAll_EmptiesThatPersonsListAndNobodyElses() throws Exception {
        eightySix(gardenToast, kit);
        message(kit, "Fryer is down");
        List<Long> burgers = checkWithBurgers(w5, sarah, 1);
        complete(burgers.get(0), kit);
        assertEquals(3, bellOf(sarah).size());

        mockMvc.perform(post("/api/notifications/dismiss-all").with(sarah)).andExpect(status().isNoContent());

        assertEquals(List.of(), textsFor(sarah));
        assertEquals(List.of("Fryer is down", "Garden Toast is 86'd"), textsFor(dan));

        // Something new after that is on her list again
        message(dan, "Last call in ten");
        assertEquals(List.of("Last call in ten"), textsFor(sarah));
    }

    @Test
    void testANotificationsTime_IsWrittenOnTheBackendsOwnClock_LikeEveryOtherStamp() throws Exception {
        // WHAT: Test how "when it happened" is written in the answer
        // WHY: A screen works out "5m ago" by comparing a stamp with the backend's time,
        //      which every answer carries in the X-Server-Time header as a local date and
        //      time with no zone (see ServerTimeFilter and the frontend's serverClock.js).
        //      That only works if the stamp is written the same way. A notification's
        //      time was sent as a moment in UTC ("...Z"): on a tablet set to another
        //      time zone than the backend, "5m ago" came out hours wrong.

        // Given - something that has just happened
        eightySix(gardenToast, kit);

        // When
        MvcResult result = mockMvc.perform(get("/api/notifications").with(sarah)).andExpect(status().isOk()).andReturn();
        String createdAt = objectMapper.readTree(result.getResponse().getContentAsString()).get(0).get("createdAt").asString();
        String backendNow = result.getResponse().getHeader("X-Server-Time");

        // Then - no zone on it, exactly like the header...
        assertTrue(createdAt.matches("^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,9})?$"),
                "a notification's time should be a local date and time with no zone, but was " + createdAt);
        // ...and read the same way as the header, it is "a moment ago"
        Duration ago = Duration.between(LocalDateTime.parse(createdAt), LocalDateTime.parse(backendNow));
        assertTrue(ago.abs().getSeconds() < 30, "created " + createdAt + ", backend's time " + backendNow);
    }

    @Test
    void testANotificationOlderThan24Hours_IsNoLongerListedOrDismissable() throws Exception {
        eightySix(gardenToast, kit);
        long id = bellOf(sarah).get(0).get("notificationId").asLong();

        jdbc.update("UPDATE notifications SET created_at = created_at - INTERVAL '25 hours'");

        assertEquals(List.of(), textsFor(sarah));
        mockMvc.perform(post("/api/notifications/" + id + "/dismiss").with(sarah)).andExpect(status().isNotFound());
    }

    @Test
    void testWithNobodySignedIn_EveryNotificationEndpointIsRefused() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/notifications/1/dismiss")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/notifications/dismiss-all")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/notifications/messages").contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"x\"}"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Written with what caused it, or not at all
    // ------------------------------------------------------------------

    @Test
    void testTheAppsOwnNotices_CanOnlyBeWrittenInsideTheTransactionOfWhatCausedThem() {
        // WHAT: Call the two "the app sends this" methods directly, with no transaction open
        // WHY: They are written in the same transaction as the 86 or the completion, so
        //      that one cannot be saved without the other. Called on their own they
        //      refuse, and nothing is written.
        StaffSession kitSession = new StaffSession(9L, "Kit", StaffRole.KITCHEN, Instant.now(), false);
        var order = new com.notapos.entity.Order();
        order.setOrderId(1L);
        order.setTableId(w5.getTableId());
        order.setStaffId(7L);

        assertThrows(IllegalTransactionStateException.class, () -> notificationService.itemEightySixChanged(gardenToast, kitSession));
        assertThrows(IllegalTransactionStateException.class, () -> notificationService.orderUp(order, kitSession));

        assertEquals(0, notificationRepository.count());
    }

    @Test
    void testEightySixingAnItemThatDoesNotExist_WritesNoNotice() throws Exception {
        // WHAT: The change fails (404)
        // WHY: No change, no notice
        mockMvc.perform(put("/api/menu-items/999999/86").with(kit)).andExpect(status().isNotFound());

        assertEquals(0, notificationRepository.count());
    }
}
