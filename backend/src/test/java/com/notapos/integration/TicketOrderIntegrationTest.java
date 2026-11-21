package com.notapos.integration;

import com.notapos.entity.MenuItem;
import com.notapos.entity.Order;
import com.notapos.entity.RestaurantTable;
import com.notapos.repository.MenuItemRepository;
import com.notapos.repository.OrderItemModifierRepository;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.PaymentRepository;
import com.notapos.repository.TableRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import java.math.BigDecimal;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the order items are listed in: the order the server
 * rang them in, on every screen.
 *
 * THE FLOOR SCENARIO: a server taps Soup, Steak, Pie and presses Send. The
 * order screen saves the three items with three requests at the same moment
 * (so that Send never misses one that is still being saved). Those requests
 * reach the backend in whatever order the network delivers them, and each
 * item's ID is handed out on arrival. The kitchen ticket was sorted by ID, so
 * it could read Steak, Pie, Soup: the cook starts the main before the starter.
 *
 * THE FIX: every ring-in request says where the item sits on the order
 * ("ringInPosition": 0 for the first item, 1 for the second, ...). The backend
 * saves it, and every list of an order's items is sorted by it.
 *
 * These tests send the requests in the "wrong" order on purpose, one after
 * another, which is exactly what out-of-order arrival looks like to the backend.
 *
 * @author CJ
 */
class TicketOrderIntegrationTest extends BaseIntegrationTest {

    @Autowired private TableRepository tableRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderItemModifierRepository orderItemModifierRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MenuItemRepository menuItemRepository;

    private Order order;
    private MenuItem soup;
    private MenuItem steak;
    private MenuItem pie;

    @BeforeEach
    void setUp() {
        // Clean every table these tests touch (nothing rolls back by itself here)
        orderItemModifierRepository.deleteAll();
        orderItemRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        menuItemRepository.deleteAll();
        tableRepository.deleteAll();

        RestaurantTable table = new RestaurantTable();
        table.setTableNumber("W1");
        table.setSeatCount(2);
        table.setStatus("occupied");
        table = tableRepository.save(table);

        order = new Order();
        order.setTableId(table.getTableId());
        order.setStatus("open");
        order = orderRepository.save(order);

        soup = menuItem("Soup", "8.00");
        steak = menuItem("Steak", "30.00");
        pie = menuItem("Pie", "7.00");
    }

    private MenuItem menuItem(String name, String price) {
        MenuItem item = new MenuItem();
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        item.setCategory("Savory");
        item.setIsActive(true);
        return menuItemRepository.save(item);
    }

    // Ring one item in, saying where it sits on the order. A null position leaves the field out.
    private void ringIn(MenuItem menuItem, Integer position) throws Exception {
        String positionField = position == null ? "" : ",\"ringInPosition\":" + position;

        mockMvc.perform(post("/api/order-items").with(signIn.asServer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + order.getOrderId()
                        + ",\"menuItemId\":" + menuItem.getMenuItemId()
                        + ",\"quantity\":1,\"modifierIds\":[]"
                        + positionField + "}"))
                .andExpect(status().isCreated());
    }

    // The server tapped Soup, Steak, Pie. The three saves arrive as Steak, Pie, Soup.
    private void ringInSoupSteakPieArrivingOutOfOrder() throws Exception {
        ringIn(steak, 1);
        ringIn(pie, 2);
        ringIn(soup, 0);
    }

    @Test
    void testKitchenTicket_ShouldListItemsInTheOrderTheServerRangThemIn() throws Exception {
        // WHAT: Three items saved out of order, then the Active Orders view (the kitchen ticket)
        // WHY: The floor scenario at the top of this file

        ringInSoupSteakPieArrivingOutOfOrder();

        mockMvc.perform(get("/api/orders/active").with(signIn.asKitchen()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].items[*].name", contains("Soup", "Steak", "Pie")));
    }

    @Test
    void testOrderScreen_ShouldGetAnOrdersItemsInRingInOrder() throws Exception {
        // WHAT: The list the order screen loads when a table is reopened
        // WHY: The server should find the check as they entered it. This list had no
        //      stated order at all: the database returned rows however it liked.

        ringInSoupSteakPieArrivingOutOfOrder();

        mockMvc.perform(get("/api/order-items/order/" + order.getOrderId()).with(signIn.asServer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].menuItemId", contains(
                        soup.getMenuItemId().intValue(),
                        steak.getMenuItemId().intValue(),
                        pie.getMenuItemId().intValue())));
    }

    @Test
    void testClosedCheck_ShouldListItemsInRingInOrder() throws Exception {
        // WHAT: Send the order, pay it, and read it back from the Tables screen's closed checks
        // WHY: A paid check is looked up when a guest questions it. It should read the way
        //      the order was taken.

        ringInSoupSteakPieArrivingOutOfOrder();
        mockMvc.perform(post("/api/order-items/order/" + order.getOrderId() + "/send-now").with(signIn.asServer()))
                .andExpect(status().isOk());
        // 8.00 + 30.00 + 7.00 = 45.00, plus 3% tax = 46.35
        mockMvc.perform(post("/api/payments").with(signIn.asServer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + order.getOrderId() + ",\"amount\":46.35,\"tipAmount\":0,\"paymentMethod\":\"cash\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/tables/overview").param("quickOrders", "false").with(signIn.asServer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.closedChecks[0].items[*].name", contains("Soup", "Steak", "Pie")));
    }

    @Test
    void testAnItemRungInLater_ShouldComeAfterTheOnesAlreadyOnTheOrder() throws Exception {
        // WHAT: A first round of two items, then a second round whose saves arrive out of order
        // WHY: The second round's positions carry on from the first (2 and 3), so the whole
        //      ticket reads first round, then second round, each in its own order

        ringIn(soup, 0);
        ringIn(steak, 1);

        ringIn(soup, 3);        // Second round: the server tapped Pie, then Soup...
        ringIn(pie, 2);         // ...and Pie's save arrived last

        mockMvc.perform(get("/api/orders/active").with(signIn.asKitchen()))
                .andExpect(jsonPath("$[0].items[*].name", contains("Soup", "Steak", "Pie", "Soup")));
    }

    @Test
    void testAnItemRungInWithNoPosition_ShouldGoToTheEndOfTheOrder() throws Exception {
        // WHAT: A request that says nothing about position, onto an order that has items
        // WHY: Something other than this app's order screen may ring an item in. The safe
        //      place for an item that does not say where it belongs is after everything
        //      already there, never ahead of it.

        ringIn(steak, 4);
        ringIn(soup, 2);

        ringIn(pie, null);

        mockMvc.perform(get("/api/orders/active").with(signIn.asKitchen()))
                .andExpect(jsonPath("$[0].items[*].name", contains("Soup", "Steak", "Pie")));
    }

    @Test
    void testTwoItemsWithTheSamePosition_ShouldKeepTheOrderTheyWereSavedIn() throws Exception {
        // WHAT: Two items that both claim position 0
        // WHY: Two terminals with the same table up can each ring in "the next item". Neither
        //      is wrong, so the tie is settled the way everything was sorted before: by ID,
        //      which is the order the backend saved them in. The list must never shuffle.

        ringIn(steak, 0);
        ringIn(soup, 0);

        mockMvc.perform(get("/api/orders/active").with(signIn.asKitchen()))
                .andExpect(jsonPath("$[0].items[*].name", contains("Steak", "Soup")));
    }

    @Test
    void testRingIn_ShouldAnswerWithThePositionItSaved() throws Exception {
        // WHAT: The answer to a ring-in
        // WHY: The order screen keeps each saved item's position, so it can work out where
        //      the next item goes without asking again

        mockMvc.perform(post("/api/order-items").with(signIn.asServer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + order.getOrderId() + ",\"menuItemId\":" + soup.getMenuItemId()
                        + ",\"quantity\":1,\"modifierIds\":[],\"ringInPosition\":5}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.ringInPosition").value(5));
    }

    @Test
    void testRingIn_WithANegativePosition_ShouldBeRefused() throws Exception {
        // WHAT: "ringInPosition": -1
        // WHY: A position below zero would put an item ahead of the first thing the server
        //      rang in. No screen sends one; a request that does is refused before anything
        //      is saved.

        mockMvc.perform(post("/api/order-items").with(signIn.asServer())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + order.getOrderId() + ",\"menuItemId\":" + soup.getMenuItemId()
                        + ",\"quantity\":1,\"modifierIds\":[],\"ringInPosition\":-1}"))
                .andExpect(status().isBadRequest());
    }
}
