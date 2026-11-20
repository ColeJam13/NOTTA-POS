package com.notapos.integration;

import com.notapos.entity.MenuItem;
import com.notapos.entity.MenuItemModifierGroup;
import com.notapos.entity.Modifier;
import com.notapos.entity.ModifierGroup;
import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.entity.RestaurantTable;
import com.notapos.repository.MenuItemModifierGroupRepository;
import com.notapos.repository.MenuItemRepository;
import com.notapos.repository.ModifierGroupRepository;
import com.notapos.repository.ModifierRepository;
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
import java.time.LocalDateTime;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for ringing items in: real requests, real database.
 *
 * These prove the rules a request must not be able to break, whatever it
 * contains: an item only goes onto an OPEN order that exists, the price comes
 * from the menu and never from the request, and the menu's own rules (active,
 * not 86'd, required modifiers) always apply.
 *
 * THE FLOOR SCENARIO behind the closed-order tests: server A has a table's
 * order screen open. Server B closes that check on another terminal. A adds a
 * round and presses Send. Before this was fixed the items attached to the
 * closed order: never billed, and never seen by the kitchen, because Active
 * Orders lists open orders only.
 *
 * @author CJ
 */
class RingInIntegrationTest extends BaseIntegrationTest {

    @Autowired private TableRepository tableRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderItemModifierRepository orderItemModifierRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private ModifierGroupRepository modifierGroupRepository;
    @Autowired private ModifierRepository modifierRepository;
    @Autowired private MenuItemModifierGroupRepository menuItemModifierGroupRepository;

    private Order openOrder;
    private Order closedOrder;
    private MenuItem chickenCutty;      // $17.00, no modifiers
    private MenuItem comboPlate;        // $15.00, one required "Bread Choice" group
    private Modifier frenchToast;       // +$2.00
    private Modifier whiteToast;        // free

    @BeforeEach
    void setUp() {
        // Clean every table these tests touch (nothing rolls back by itself here)
        orderItemModifierRepository.deleteAll();
        orderItemRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        menuItemModifierGroupRepository.deleteAll();
        modifierRepository.deleteAll();
        modifierGroupRepository.deleteAll();
        menuItemRepository.deleteAll();
        tableRepository.deleteAll();

        RestaurantTable table = new RestaurantTable();
        table.setTableNumber("W1");
        table.setSeatCount(2);
        table.setStatus("occupied");
        table = tableRepository.save(table);

        RestaurantTable otherTable = new RestaurantTable();
        otherTable.setTableNumber("W2");
        otherTable.setSeatCount(2);
        otherTable.setStatus("available");
        otherTable = tableRepository.save(otherTable);

        openOrder = new Order();
        openOrder.setTableId(table.getTableId());
        openOrder.setStatus("open");
        openOrder = orderRepository.save(openOrder);

        // A check that was paid on another terminal
        closedOrder = new Order();
        closedOrder.setTableId(otherTable.getTableId());
        closedOrder.setStatus("closed");
        closedOrder.setClosedAt(LocalDateTime.now());
        closedOrder = orderRepository.save(closedOrder);

        chickenCutty = menuItem("Chicken Cutty", "17.00");
        comboPlate = menuItem("Combo Plate", "15.00");

        ModifierGroup breadChoice = new ModifierGroup();
        breadChoice.setName("Bread Choice");
        breadChoice.setIsRequired(true);
        breadChoice.setMaxSelections(1);
        breadChoice.setIsActive(true);
        breadChoice = modifierGroupRepository.save(breadChoice);

        whiteToast = modifier(breadChoice, "White Toast", "0.00");
        frenchToast = modifier(breadChoice, "French Toast", "2.00");

        MenuItemModifierGroup link = new MenuItemModifierGroup();
        link.setMenuItemId(comboPlate.getMenuItemId());
        link.setModifierGroupId(breadChoice.getModifierGroupId());
        menuItemModifierGroupRepository.save(link);
    }

    private MenuItem menuItem(String name, String price) {
        MenuItem item = new MenuItem();
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        item.setCategory("Savory");
        item.setIsActive(true);
        return menuItemRepository.save(item);
    }

    private Modifier modifier(ModifierGroup group, String name, String priceAdjustment) {
        Modifier modifier = new Modifier();
        modifier.setModifierGroupId(group.getModifierGroupId());
        modifier.setName(name);
        modifier.setPriceAdjustment(new BigDecimal(priceAdjustment));
        modifier.setIsActive(true);
        return modifierRepository.save(modifier);
    }

    // The JSON body for ringing in one item. "extra" lets a test slip in fields a well-behaved screen would not send.
    private String ringIn(Order order, MenuItem menuItem, String modifierIds, String extra) {
        return "{\"orderId\":" + order.getOrderId()
                + ",\"menuItemId\":" + menuItem.getMenuItemId()
                + ",\"quantity\":1"
                + ",\"modifierIds\":[" + modifierIds + "]"
                + extra + "}";
    }

    // What a screen from before this fix added to every plain ring-in. Tests that send it
    // prove the rule holds even for a request the old code would have accepted.
    private static final String OLD_SCREEN_PRICE = ",\"price\":17.00,\"status\":\"draft\"";

    private long itemsOn(Order order) {
        return orderItemRepository.findByOrderId(order.getOrderId()).size();
    }

    // A draft item saved straight into the database, as if it had been rung in earlier
    private OrderItem savedItem(Order order, MenuItem menuItem, String status) {
        OrderItem item = new OrderItem();
        item.setOrderId(order.getOrderId());
        item.setMenuItemId(menuItem.getMenuItemId());
        item.setQuantity(1);
        item.setPrice(menuItem.getPrice());
        item.setStatus(status);
        item.setIsLocked(false);
        return orderItemRepository.save(item);
    }

    // ------------------------------------------------------------------
    // The price comes from the menu
    // ------------------------------------------------------------------

    @Test
    void testRingIn_ShouldSaveThePlainItemAtTheMenuPrice() throws Exception {
        // WHAT: Ring in an item with no modifiers
        // WHY: The everyday case must keep working through the one ring-in path

        mockMvc.perform(post("/api/order-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ringIn(openOrder, chickenCutty, "", "")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(17.00))
                .andExpect(jsonPath("$.status").value("draft"));

        assertEquals(1, itemsOn(openOrder));
    }

    @Test
    void testRingIn_WithAMadeUpPrice_ShouldStillSaveTheMenuPrice() throws Exception {
        // WHAT: Ring in a $17.00 item with "price": 0.01 in the request
        // WHY: The plain ring-in path used to save whatever price the browser sent. Anyone
        //      who could reach the backend could ring in a steak for a cent.

        mockMvc.perform(post("/api/order-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ringIn(openOrder, chickenCutty, "", ",\"price\":0.01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(17.00));

        OrderItem saved = orderItemRepository.findByOrderId(openOrder.getOrderId()).get(0);
        assertEquals(0, new BigDecimal("17.00").compareTo(saved.getPrice()));
    }

    @Test
    void testRingIn_WithModifiers_ShouldAddTheUpchargeToTheMenuPrice() throws Exception {
        // WHAT: Ring in a Combo Plate ($15.00) with French Toast (+$2.00)
        // WHY: The same path prices items with modifiers: menu price plus upcharges

        mockMvc.perform(post("/api/order-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ringIn(openOrder, comboPlate, String.valueOf(frenchToast.getModifierId()), ",\"price\":0.01")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.price").value(17.00));

        assertEquals(1, orderItemModifierRepository.count());
    }

    // ------------------------------------------------------------------
    // Only onto an open order that exists
    // ------------------------------------------------------------------

    @Test
    void testRingIn_OntoAClosedOrder_ShouldBeRefusedAndSaveNothing() throws Exception {
        // WHAT: Ring in an item onto a check that was already paid
        // WHY: The floor scenario at the top of this file. The screen is told the check was
        //      closed (409, with a code it can recognize) so it can reload the table.

        mockMvc.perform(post("/api/order-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ringIn(closedOrder, chickenCutty, "", OLD_SCREEN_PRICE)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_OPEN"));

        assertEquals(0, itemsOn(closedOrder));
    }

    @Test
    void testRingIn_OntoAnOrderThatDoesNotExist_ShouldBeRefusedAndSaveNothing() throws Exception {
        // WHAT: Ring in an item with an order ID that is not in the database
        // WHY: Tables are linked by plain ID columns, so the database itself would accept an
        //      item pointing at nothing

        mockMvc.perform(post("/api/order-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":999999,\"menuItemId\":" + chickenCutty.getMenuItemId() + ",\"quantity\":1,\"modifierIds\":[]" + OLD_SCREEN_PRICE + "}"))
                .andExpect(status().isNotFound());

        assertEquals(0, orderItemRepository.count());
    }

    // ------------------------------------------------------------------
    // The menu's rules always apply
    // ------------------------------------------------------------------

    @Test
    void testRingIn_AnItemThatIsOffTheMenu_ShouldBeRefused() throws Exception {
        // WHAT: Ring in a menu item that has been deactivated
        // WHY: A screen that loaded the menu before the item was removed could still send it

        chickenCutty.setIsActive(false);
        menuItemRepository.save(chickenCutty);

        mockMvc.perform(post("/api/order-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ringIn(openOrder, chickenCutty, "", OLD_SCREEN_PRICE)))
                .andExpect(status().isConflict());

        assertEquals(0, itemsOn(openOrder));
    }

    @Test
    void testRingIn_AnItemThatIs86d_ShouldBeRefused() throws Exception {
        // WHAT: Ring in an 86'd item
        // WHY: The kitchen is out of it. Same rule on the one path, with or without modifiers.

        chickenCutty.setIs86d(true);
        menuItemRepository.save(chickenCutty);

        mockMvc.perform(post("/api/order-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ringIn(openOrder, chickenCutty, "", OLD_SCREEN_PRICE)))
                .andExpect(status().isConflict());

        assertEquals(0, itemsOn(openOrder));
    }

    @Test
    void testRingIn_AMenuItemThatDoesNotExist_ShouldBeRefused() throws Exception {
        // WHAT: Ring in a menu item ID that is not in the database
        // WHY: The plain path used to save it anyway, leaving a line with no name and a price from the request

        mockMvc.perform(post("/api/order-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":" + openOrder.getOrderId() + ",\"menuItemId\":999999,\"quantity\":1,\"modifierIds\":[]" + OLD_SCREEN_PRICE + "}"))
                .andExpect(status().isNotFound());

        assertEquals(0, itemsOn(openOrder));
    }

    @Test
    void testRingIn_WithoutARequiredModifier_ShouldBeRefused() throws Exception {
        // WHAT: Ring in a Combo Plate without choosing a bread
        // WHY: The plain path used to skip modifier rules, so an item with a required choice
        //      could reach the kitchen without it

        mockMvc.perform(post("/api/order-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ringIn(openOrder, comboPlate, "", OLD_SCREEN_PRICE)))
                .andExpect(status().isBadRequest());

        assertEquals(0, itemsOn(openOrder));
    }

    // ------------------------------------------------------------------
    // Sending and changing items needs an open order too
    // ------------------------------------------------------------------

    @Test
    void testSend_OnAClosedOrder_ShouldBeRefusedAndLeaveItemsAlone() throws Exception {
        // WHAT: Press Send for a check that was already paid
        // WHY: Nothing more can be sent to the kitchen on a closed check

        OrderItem straggler = savedItem(closedOrder, chickenCutty, "draft");

        mockMvc.perform(post("/api/order-items/order/" + closedOrder.getOrderId() + "/send"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_OPEN"));

        assertEquals("draft", orderItemRepository.findById(straggler.getOrderItemId()).orElseThrow().getStatus());
    }

    @Test
    void testSendNow_OnAClosedOrder_ShouldBeRefusedAndLeaveItemsAlone() throws Exception {
        // WHAT: Press Send Now for a check that was already paid
        // WHY: Same rule as Send

        OrderItem straggler = savedItem(closedOrder, chickenCutty, "draft");

        mockMvc.perform(post("/api/order-items/order/" + closedOrder.getOrderId() + "/send-now"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_OPEN"));

        assertEquals("draft", orderItemRepository.findById(straggler.getOrderItemId()).orElseThrow().getStatus());
    }

    @Test
    void testReplaceModifiers_OnAClosedOrder_ShouldBeRefused() throws Exception {
        // WHAT: Change the modifiers of an item on a check that was already paid
        // WHY: Changing modifiers changes the price. A paid check must not change.

        OrderItem item = savedItem(closedOrder, comboPlate, "draft");

        mockMvc.perform(put("/api/order-items/" + item.getOrderItemId() + "/modifiers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"modifierIds\":[" + frenchToast.getModifierId() + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_OPEN"));

        assertEquals(0, new BigDecimal("15.00").compareTo(
                orderItemRepository.findById(item.getOrderItemId()).orElseThrow().getPrice()));
    }

    @Test
    void testSend_OnAnOpenOrder_ShouldStillWork() throws Exception {
        // WHAT: Ring in on an open order, then Send
        // WHY: The new checks must not get in the way of the normal flow

        mockMvc.perform(post("/api/order-items")
                .contentType(MediaType.APPLICATION_JSON)
                .content(ringIn(openOrder, comboPlate, String.valueOf(whiteToast.getModifierId()), "")))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/order-items/order/" + openOrder.getOrderId() + "/send"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("limbo"));

        List<OrderItem> items = orderItemRepository.findByOrderId(openOrder.getOrderId());
        assertEquals("limbo", items.get(0).getStatus());
        assertNotNull(items.get(0).getSentAt());
    }

    // ------------------------------------------------------------------
    // The old ways in are gone
    // ------------------------------------------------------------------

    @Test
    void testTheOldUpdateEndpoint_ShouldNoLongerExist() throws Exception {
        // WHAT: PUT /api/order-items/{id}, which took a price from the request
        // WHY: Nothing on the order screen used it, and it could rewrite an item's price

        OrderItem item = savedItem(openOrder, chickenCutty, "draft");

        mockMvc.perform(put("/api/order-items/" + item.getOrderItemId())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"quantity\":1,\"price\":0.01}"))
                .andExpect(status().isMethodNotAllowed());

        assertEquals(0, new BigDecimal("17.00").compareTo(
                orderItemRepository.findById(item.getOrderItemId()).orElseThrow().getPrice()));
    }
}
