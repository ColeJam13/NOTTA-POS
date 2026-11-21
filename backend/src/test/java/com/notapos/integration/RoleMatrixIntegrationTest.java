package com.notapos.integration;

import tools.jackson.databind.ObjectMapper;
import com.notapos.dto.StaffCreateRequest;
import com.notapos.entity.MenuItem;
import com.notapos.entity.MenuItemModifierGroup;
import com.notapos.entity.Modifier;
import com.notapos.entity.ModifierGroup;
import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.entity.OrderItemModifier;
import com.notapos.entity.Payment;
import com.notapos.entity.PrepStation;
import com.notapos.entity.RestaurantTable;
import com.notapos.entity.TableStatusLog;
import com.notapos.enums.OrderItemStatus;
import com.notapos.enums.OrderStatus;
import com.notapos.enums.StaffRole;
import com.notapos.repository.MenuItemModifierGroupRepository;
import com.notapos.repository.MenuItemRepository;
import com.notapos.repository.ModifierGroupRepository;
import com.notapos.repository.ModifierRepository;
import com.notapos.repository.OrderItemModifierRepository;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.PaymentRepository;
import com.notapos.repository.PrepStationRepository;
import com.notapos.repository.StaffRepository;
import com.notapos.repository.TableRepository;
import com.notapos.repository.TableStatusLogRepository;
import com.notapos.service.OrderCheckService;
import com.notapos.service.StaffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for "who may do what", for EVERY endpoint the backend has.
 *
 * WHAT: The table in setUp() lists every endpoint, a real, well-formed request
 *       for it, and the roles that may send it. It is the role matrix from
 *       Documentation/phase-2-plan.md (step A3b), written out endpoint by
 *       endpoint. The tests then:
 *         - call every endpoint as nobody, and with a made-up token      -> 401
 *         - call every endpoint as each role that is NOT on its list     -> 403
 *         - call every endpoint as each role that IS on its list         -> works
 *       and check that the refused requests left the database untouched.
 *
 * WHY:  Before this step only 7 endpoints checked who was asking. Anything
 *       that could reach the backend could change prices, delete payments, or
 *       read every tip. Hiding a button on a screen stops nobody who sends
 *       the request directly, so the rules are proven here from the outside,
 *       with real sign-ins, the way that person would try them.
 *
 * TWO GUARDS keep this honest as the backend grows:
 *   - The table must list exactly the endpoints the backend really has. Add
 *     an endpoint and forget this table, and a test fails.
 *   - Every endpoint that changes data must carry a role rule. Add one
 *     without a rule, and a test fails.
 *
 * Sign-in mechanics (who am I, signing out, CORS, cookies) are tested in
 * SignInRulesIntegrationTest.
 *
 * @author CJ
 */
class RoleMatrixIntegrationTest extends BaseIntegrationTest {

    private static final String TOKEN_HEADER = "X-Staff-Token";

    // The one endpoint that needs no sign-in: it is how a person signs in
    private static final String SIGN_IN = "POST /api/staff/sign-in";
    private static final String SIGN_OUT = "POST /api/staff/sign-out";
    private static final String CHANGE_OWN_PIN = "PUT /api/staff/me/pin";

    @Autowired private StaffService staffService;
    @Autowired private OrderCheckService orderCheckService;
    @Autowired private StaffRepository staffRepository;
    @Autowired private TableRepository tableRepository;
    @Autowired private TableStatusLogRepository tableStatusLogRepository;
    @Autowired private PrepStationRepository prepStationRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private ModifierGroupRepository modifierGroupRepository;
    @Autowired private ModifierRepository modifierRepository;
    @Autowired private MenuItemModifierGroupRepository menuItemModifierGroupRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderItemModifierRepository orderItemModifierRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;     // Every endpoint the backend has

    // A token for one signed-in person of each role, and the PIN they signed in with
    private final Map<StaffRole, String> tokens = new EnumMap<>(StaffRole.class);
    private final Map<StaffRole, String> pins = new EnumMap<>(StaffRole.class);
    private Long someoneElseId;         // A staff member the tests change (so nobody's own session is touched)
    private int nextPin = 5000;         // PINs for staff created during a test: each must be unused
    private StaffRole asking;           // Whose turn it is in the "allowed roles" test (a PIN change needs that person's own PIN)
    private String newPin;              // The PIN the last PIN-change request asked for
    private int nextName = 1;           // Names that must be unique (prep stations, table numbers)

    // The restaurant the requests act on. Rebuilt by buildRestaurant().
    private PrepStation station, spareStation;
    private MenuItem menuItem, spareMenuItem;
    private ModifierGroup group, spareGroup;
    private Modifier modifier, spareModifier;
    private MenuItemModifierGroup link;
    private RestaurantTable busyTable, emptyCheckTable, freeTable, payTable, paidTable;
    private Order openOrder;            // On busyTable: one draft, one pending, and one fired item
    private Order emptyOrder;           // On emptyCheckTable: open, nothing on it (it can be cleared)
    private Order payOrder;             // On payTable: one sent item, ready to be paid
    private Order closedOrder;          // On paidTable: paid and closed
    private OrderItem draftItem, pendingItem, firedItem;
    private OrderItemModifier itemModifier;
    private Payment payment;
    private TableStatusLog statusLog;

    // One endpoint: its name ("PUT /api/menu-items/{id}/86"), who may use it, and how to build a real request for it
    private record Endpoint(String key, Set<StaffRole> allowed, Supplier<MockHttpServletRequestBuilder> request) {
    }

    private final Map<String, Endpoint> endpoints = new LinkedHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        staffRepository.deleteAll();
        buildRestaurant();

        // One person per role, each with their own PIN, signed in the way the PIN pad does it
        int pin = 2001;
        for (StaffRole role : StaffRole.values()) {
            String pinText = String.valueOf(pin++);
            staffService.createStaff(new StaffCreateRequest("The " + role.getValue(), role.getValue(), pinText));
            pins.put(role, pinText);
            tokens.put(role, signIn(pinText));
        }
        someoneElseId = staffService.createStaff(new StaffCreateRequest("Someone Else", "server", "2999")).staffId();

        // ==================================================================
        // THE ROLE MATRIX (Documentation/phase-2-plan.md, step A3b)
        // ==================================================================
        Set<StaffRole> anyoneSignedIn = EnumSet.allOf(StaffRole.class);
        Set<StaffRole> takesOrders = EnumSet.of(StaffRole.SERVER, StaffRole.BARTENDER, StaffRole.HOST, StaffRole.MANAGER);
        Set<StaffRole> makesOrders = EnumSet.of(StaffRole.KITCHEN, StaffRole.BARTENDER, StaffRole.MANAGER);
        Set<StaffRole> knowsWhatRanOut = EnumSet.of(StaffRole.KITCHEN, StaffRole.BARTENDER, StaffRole.MANAGER);
        Set<StaffRole> managerOnly = EnumSet.of(StaffRole.MANAGER);

        // ------------------------------------------------------------------
        // "Read menu, tables, orders, active orders": any signed-in staff
        // ------------------------------------------------------------------
        endpoint("GET /api/orders/active", anyoneSignedIn, () -> get("/api/orders/active"));

        endpoint("GET /api/menu-items", anyoneSignedIn, () -> get("/api/menu-items"));
        endpoint("GET /api/menu-items/{id}", anyoneSignedIn, () -> get("/api/menu-items/" + menuItem.getMenuItemId()));
        endpoint("GET /api/menu-items/category/{category}", anyoneSignedIn, () -> get("/api/menu-items/category/Savory"));
        endpoint("GET /api/menu-items/prep-station/{prepStationId}", anyoneSignedIn, () -> get("/api/menu-items/prep-station/" + station.getPrepStationId()));
        endpoint("GET /api/menu-items/86", anyoneSignedIn, () -> get("/api/menu-items/86"));
        endpoint("GET /api/menu-items/modifier-groups", anyoneSignedIn, () -> get("/api/menu-items/modifier-groups"));

        endpoint("GET /api/menu-item-modifier-groups", anyoneSignedIn, () -> get("/api/menu-item-modifier-groups"));
        endpoint("GET /api/menu-item-modifier-groups/{id}", anyoneSignedIn, () -> get("/api/menu-item-modifier-groups/" + link.getMenuItemModifierGroupId()));
        endpoint("GET /api/menu-item-modifier-groups/menu-item/{menuItemId}", anyoneSignedIn, () -> get("/api/menu-item-modifier-groups/menu-item/" + menuItem.getMenuItemId()));
        endpoint("GET /api/menu-item-modifier-groups/modifier-group/{modifierGroupId}", anyoneSignedIn, () -> get("/api/menu-item-modifier-groups/modifier-group/" + group.getModifierGroupId()));

        endpoint("GET /api/modifiers", anyoneSignedIn, () -> get("/api/modifiers"));
        endpoint("GET /api/modifiers/{id}", anyoneSignedIn, () -> get("/api/modifiers/" + modifier.getModifierId()));
        endpoint("GET /api/modifiers/group/{modifierGroupId}", anyoneSignedIn, () -> get("/api/modifiers/group/" + group.getModifierGroupId()));
        endpoint("GET /api/modifier-groups", anyoneSignedIn, () -> get("/api/modifier-groups"));
        endpoint("GET /api/modifier-groups/{id}", anyoneSignedIn, () -> get("/api/modifier-groups/" + group.getModifierGroupId()));

        endpoint("GET /api/orders", anyoneSignedIn, () -> get("/api/orders"));
        endpoint("GET /api/orders/{id}", anyoneSignedIn, () -> get("/api/orders/" + openOrder.getOrderId()));
        endpoint("GET /api/orders/{id}/check", anyoneSignedIn, () -> get("/api/orders/" + openOrder.getOrderId() + "/check"));
        endpoint("GET /api/orders/table/{tableId}", anyoneSignedIn, () -> get("/api/orders/table/" + busyTable.getTableId()));

        endpoint("GET /api/order-items", anyoneSignedIn, () -> get("/api/order-items"));
        endpoint("GET /api/order-items/{id}", anyoneSignedIn, () -> get("/api/order-items/" + draftItem.getOrderItemId()));
        endpoint("GET /api/order-items/order/{orderId}", anyoneSignedIn, () -> get("/api/order-items/order/" + openOrder.getOrderId()));

        endpoint("GET /api/order-item-modifiers", anyoneSignedIn, () -> get("/api/order-item-modifiers"));
        endpoint("GET /api/order-item-modifiers/{id}", anyoneSignedIn, () -> get("/api/order-item-modifiers/" + itemModifier.getOrderItemModifierId()));
        endpoint("GET /api/order-item-modifiers/order-item/{orderItemId}", anyoneSignedIn, () -> get("/api/order-item-modifiers/order-item/" + draftItem.getOrderItemId()));
        endpoint("GET /api/order-item-modifiers/modifier/{modifierId}", anyoneSignedIn, () -> get("/api/order-item-modifiers/modifier/" + modifier.getModifierId()));
        endpoint("GET /api/order-item-modifiers/order/{orderId}", anyoneSignedIn, () -> get("/api/order-item-modifiers/order/" + openOrder.getOrderId()));

        endpoint("GET /api/prep-stations", anyoneSignedIn, () -> get("/api/prep-stations"));
        endpoint("GET /api/prep-stations/{id}", anyoneSignedIn, () -> get("/api/prep-stations/" + station.getPrepStationId()));

        endpoint("GET /api/tables", anyoneSignedIn, () -> get("/api/tables"));
        endpoint("GET /api/tables/{id}", anyoneSignedIn, () -> get("/api/tables/" + busyTable.getTableId()));
        endpoint("GET /api/tables/overview", anyoneSignedIn, () -> get("/api/tables/overview").param("quickOrders", "false"));

        endpoint("GET /api/table-status-logs", anyoneSignedIn, () -> get("/api/table-status-logs"));
        endpoint("GET /api/table-status-logs/{id}", anyoneSignedIn, () -> get("/api/table-status-logs/" + statusLog.getLogId()));
        endpoint("GET /api/table-status-logs/table/{tableId}", anyoneSignedIn, () -> get("/api/table-status-logs/table/" + busyTable.getTableId()));

        // The settings a screen needs (the tax rate for the running total): anyone who is signed in
        endpoint("GET /api/settings", anyoneSignedIn, () -> get("/api/settings"));
        // The restaurant's template (the look of every screen): the manager's choice (step B4b)
        endpoint("PUT /api/settings/template", managerOnly, () -> json(put("/api/settings/template"), "{\"template\":\"8-bit\"}"));

        // Who am I, and signing out: anyone who is signed in
        endpoint("GET /api/staff/me", anyoneSignedIn, () -> get("/api/staff/me"));
        endpoint(SIGN_OUT, anyoneSignedIn, () -> post("/api/staff/sign-out"));
        // Choosing a new PIN for yourself: the request must carry the asker's own current PIN
        endpoint(CHANGE_OWN_PIN, anyoneSignedIn, () -> {
            newPin = String.valueOf(nextPin++);
            return json(put("/api/staff/me/pin"),
                    "{\"currentPin\":\"" + pins.getOrDefault(asking, "0000") + "\",\"newPin\":\"" + newPin + "\"}");
        });

        // ------------------------------------------------------------------
        // "Open orders, ring in, send, take payment": server, bartender, host, manager
        // ------------------------------------------------------------------
        endpoint("POST /api/orders", takesOrders, () -> json(post("/api/orders"),
                "{\"tableId\":" + freeTable.getTableId() + ",\"orderType\":\"dine_in\"}"));
        endpoint("DELETE /api/orders/{id}", takesOrders, () -> delete("/api/orders/" + emptyOrder.getOrderId()));      // Clear an empty check off its table

        endpoint("POST /api/order-items", takesOrders, () -> json(post("/api/order-items"),
                "{\"orderId\":" + openOrder.getOrderId() + ",\"menuItemId\":" + menuItem.getMenuItemId() + "}"));
        endpoint("PUT /api/order-items/{id}/modifiers", takesOrders, () -> json(put("/api/order-items/" + draftItem.getOrderItemId() + "/modifiers"),
                "{\"specialInstructions\":\"no onions\",\"modifierIds\":[" + modifier.getModifierId() + "]}"));
        endpoint("POST /api/order-items/order/{orderId}/send", takesOrders, () -> post("/api/order-items/order/" + openOrder.getOrderId() + "/send"));
        endpoint("POST /api/order-items/order/{orderId}/send-now", takesOrders, () -> post("/api/order-items/order/" + openOrder.getOrderId() + "/send-now"));
        endpoint("PUT /api/order-items/{id}/send-now", takesOrders, () -> put("/api/order-items/" + draftItem.getOrderItemId() + "/send-now"));
        endpoint("DELETE /api/order-items/{id}", takesOrders, () -> delete("/api/order-items/" + draftItem.getOrderItemId()));

        endpoint("POST /api/payments", takesOrders, () -> json(post("/api/payments"),
                "{\"orderId\":" + payOrder.getOrderId() + ",\"amount\":" + orderCheckService.getCheck(payOrder.getOrderId()).total()
                        + ",\"paymentMethod\":\"cash\"}"));

        // Starting a Quick Order makes its own "table" row. (A table on the floor is the
        // manager's: see the tests further down.)
        endpoint("POST /api/tables", takesOrders, () -> json(post("/api/tables"),
                "{\"tableNumber\":\"QO" + (nextName++) + "\",\"section\":\"Quick Orders\",\"seatCount\":0,\"status\":\"available\",\"isQuickOrder\":true}"));

        // ------------------------------------------------------------------
        // "Start and complete items": kitchen, bartender, manager
        // ------------------------------------------------------------------
        endpoint("PUT /api/order-items/{id}/start", makesOrders, () -> put("/api/order-items/" + pendingItem.getOrderItemId() + "/start"));
        endpoint("PUT /api/order-items/{id}/complete", makesOrders, () -> put("/api/order-items/" + firedItem.getOrderItemId() + "/complete"));

        // ------------------------------------------------------------------
        // "86 and un-86": kitchen, bartender, manager
        // ------------------------------------------------------------------
        endpoint("PUT /api/menu-items/{id}/86", knowsWhatRanOut, () -> put("/api/menu-items/" + menuItem.getMenuItemId() + "/86"));
        endpoint("PUT /api/menu-items/{id}/un-86", knowsWhatRanOut, () -> put("/api/menu-items/" + menuItem.getMenuItemId() + "/un-86"));

        // ------------------------------------------------------------------
        // "Menu items, modifiers, prep stations: create, change, remove": manager
        // ------------------------------------------------------------------
        endpoint("POST /api/menu-items", managerOnly, () -> json(post("/api/menu-items"),
                "{\"name\":\"Biscuit\",\"price\":4.00,\"category\":\"Savory\"}"));
        endpoint("PUT /api/menu-items/{id}", managerOnly, () -> json(put("/api/menu-items/" + menuItem.getMenuItemId()),
                "{\"name\":\"Chicken Cutty\",\"price\":1.00,\"category\":\"Savory\",\"isActive\":true}"));
        endpoint("PUT /api/menu-items/{id}/deactivate", managerOnly, () -> put("/api/menu-items/" + menuItem.getMenuItemId() + "/deactivate"));
        endpoint("PUT /api/menu-items/{id}/activate", managerOnly, () -> put("/api/menu-items/" + menuItem.getMenuItemId() + "/activate"));
        endpoint("DELETE /api/menu-items/{id}", managerOnly, () -> delete("/api/menu-items/" + spareMenuItem.getMenuItemId()));

        endpoint("POST /api/menu-item-modifier-groups", managerOnly, () -> json(post("/api/menu-item-modifier-groups"),
                "{\"menuItemId\":" + spareMenuItem.getMenuItemId() + ",\"modifierGroupId\":" + group.getModifierGroupId() + "}"));
        endpoint("DELETE /api/menu-item-modifier-groups/{id}", managerOnly, () -> delete("/api/menu-item-modifier-groups/" + link.getMenuItemModifierGroupId()));

        endpoint("POST /api/modifiers", managerOnly, () -> json(post("/api/modifiers"),
                "{\"modifierGroupId\":" + group.getModifierGroupId() + ",\"name\":\"Rye Toast\",\"priceAdjustment\":0.50}"));
        endpoint("PUT /api/modifiers/{id}", managerOnly, () -> json(put("/api/modifiers/" + modifier.getModifierId()),
                "{\"modifierGroupId\":" + group.getModifierGroupId() + ",\"name\":\"French Toast\",\"priceAdjustment\":0.00,\"isActive\":true}"));
        endpoint("DELETE /api/modifiers/{id}", managerOnly, () -> delete("/api/modifiers/" + spareModifier.getModifierId()));

        endpoint("POST /api/modifier-groups", managerOnly, () -> json(post("/api/modifier-groups"),
                "{\"name\":\"Sides\",\"isRequired\":false,\"maxSelections\":2,\"isActive\":true}"));
        endpoint("PUT /api/modifier-groups/{id}", managerOnly, () -> json(put("/api/modifier-groups/" + group.getModifierGroupId()),
                "{\"name\":\"Bread\",\"isRequired\":false,\"maxSelections\":1,\"isActive\":true}"));
        endpoint("DELETE /api/modifier-groups/{id}", managerOnly, () -> delete("/api/modifier-groups/" + spareGroup.getModifierGroupId()));

        endpoint("POST /api/prep-stations", managerOnly, () -> json(post("/api/prep-stations"),
                "{\"name\":\"Fryer " + (nextName++) + "\",\"isActive\":true}"));
        endpoint("PUT /api/prep-stations/{id}", managerOnly, () -> json(put("/api/prep-stations/" + station.getPrepStationId()),
                "{\"name\":\"Renamed " + (nextName++) + "\",\"isActive\":true}"));
        endpoint("DELETE /api/prep-stations/{id}", managerOnly, () -> delete("/api/prep-stations/" + spareStation.getPrepStationId()));

        // ------------------------------------------------------------------
        // "Staff, Financials, floor layout": manager
        // ------------------------------------------------------------------
        endpoint("GET /api/staff", managerOnly, () -> get("/api/staff"));
        endpoint("POST /api/staff", managerOnly, () -> json(post("/api/staff"),
                "{\"name\":\"New Person\",\"role\":\"server\",\"pin\":\"" + (nextPin++) + "\"}"));
        endpoint("PUT /api/staff/{id}", managerOnly, () -> json(put("/api/staff/" + someoneElseId), "{\"name\":\"Renamed\"}"));
        // Terminals paused for wrong PINs: seeing them and releasing one (step A6, decision D11)
        endpoint("GET /api/staff/paused-devices", managerOnly, () -> get("/api/staff/paused-devices"));
        endpoint("POST /api/staff/paused-devices/release", managerOnly, () -> json(post("/api/staff/paused-devices/release"),
                "{\"device\":\"192.168.1.21\"}"));
        endpoint("GET /api/reports/financials", managerOnly, () -> get("/api/reports/financials")
                .param("from", "2026-11-18T00:00:00").param("to", "2026-11-19T00:00:00"));
        endpoint("PUT /api/tables/layout", managerOnly, () -> json(put("/api/tables/layout"),
                "{\"tables\":[{\"tableId\":" + busyTable.getTableId() + ",\"x\":100,\"y\":120,\"rotation\":0}]}"));

        // ------------------------------------------------------------------
        // "Read payments and tips": manager
        // ------------------------------------------------------------------
        endpoint("GET /api/payments", managerOnly, () -> get("/api/payments"));
        endpoint("GET /api/payments/{id}", managerOnly, () -> get("/api/payments/" + payment.getPaymentId()));
        endpoint("GET /api/payments/order/{orderId}", managerOnly, () -> get("/api/payments/order/" + closedOrder.getOrderId()));

        // ------------------------------------------------------------------
        // Not named in the matrix, and no screen uses them: manager, until a feature needs
        // one and opens it on purpose
        // ------------------------------------------------------------------
        endpoint("PUT /api/tables/{id}/status", managerOnly, () -> put("/api/tables/" + freeTable.getTableId() + "/status").param("status", "reserved"));
        endpoint("PUT /api/tables/{id}", managerOnly, () -> json(put("/api/tables/" + freeTable.getTableId()), "{\"status\":\"reserved\"}"));
        endpoint("POST /api/order-item-modifiers", managerOnly, () -> json(post("/api/order-item-modifiers"),
                "{\"orderItemId\":" + pendingItem.getOrderItemId() + ",\"modifierId\":" + modifier.getModifierId() + ",\"priceAdjustment\":2.00}"));
        endpoint("DELETE /api/order-item-modifiers/{id}", managerOnly, () -> delete("/api/order-item-modifiers/" + itemModifier.getOrderItemModifierId()));
        endpoint("POST /api/table-status-logs", managerOnly, () -> json(post("/api/table-status-logs"),
                "{\"tableId\":" + busyTable.getTableId() + ",\"oldStatus\":\"available\",\"newStatus\":\"occupied\"}"));
        endpoint("DELETE /api/table-status-logs/{id}", managerOnly, () -> delete("/api/table-status-logs/" + statusLog.getLogId()));
        endpoint("POST /api/order-items/lock-expired", managerOnly, () -> post("/api/order-items/lock-expired"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void endpoint(String key, Set<StaffRole> allowed, Supplier<MockHttpServletRequestBuilder> request) {
        assertNull(endpoints.put(key, new Endpoint(key, allowed, request)), key + " is in the table twice");
    }

    private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.contentType(MediaType.APPLICATION_JSON).content(body);
    }

    // Sign in with a PIN through the real endpoint and return the token it hands back
    private String signIn(String pin) throws Exception {
        MvcResult result = mockMvc.perform(json(post("/api/staff/sign-in"), "{\"pin\":\"" + pin + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
    }

    /**
     * Empty the restaurant and build it again, the same every time.
     *
     * WHY: A request that works changes things: the draft item is sent, the empty
     *      check is cleared, the spare menu item is deleted. The next request in the
     *      table needs them back. Staff are left alone, so everyone stays signed in.
     */
    private void buildRestaurant() {
        orderItemModifierRepository.deleteAll();
        orderItemRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        menuItemModifierGroupRepository.deleteAll();
        modifierRepository.deleteAll();
        modifierGroupRepository.deleteAll();
        menuItemRepository.deleteAll();
        prepStationRepository.deleteAll();
        tableStatusLogRepository.deleteAll();
        tableRepository.deleteAll();

        station = prepStation("Grill");
        spareStation = prepStation("Spare station");

        menuItem = menuItem("Chicken Cutty", "17.00");
        spareMenuItem = menuItem("Not on any order", "9.00");

        group = modifierGroup("Bread Choice");                       // Optional, so an item can be rung in without it
        spareGroup = modifierGroup("Not on any item");
        modifier = modifier(group, "French Toast", "2.00");
        spareModifier = modifier(spareGroup, "Not on any order", "1.00");

        link = new MenuItemModifierGroup();                          // Chicken Cutty offers Bread Choice
        link.setMenuItemId(menuItem.getMenuItemId());
        link.setModifierGroupId(group.getModifierGroupId());
        link = menuItemModifierGroupRepository.save(link);

        busyTable = table("W1", "occupied");
        emptyCheckTable = table("W2", "occupied");
        freeTable = table("W3", "available");
        payTable = table("W4", "occupied");
        paidTable = table("W5", "available");

        openOrder = order(busyTable, OrderStatus.OPEN);
        draftItem = item(openOrder, OrderItemStatus.DRAFT);
        pendingItem = item(openOrder, OrderItemStatus.PENDING);
        firedItem = item(openOrder, OrderItemStatus.FIRED);

        itemModifier = new OrderItemModifier();                      // The draft item has French Toast on it
        itemModifier.setOrderItemId(draftItem.getOrderItemId());
        itemModifier.setModifierId(modifier.getModifierId());
        itemModifier.setPriceAdjustment(new BigDecimal("2.00"));
        itemModifier = orderItemModifierRepository.save(itemModifier);

        emptyOrder = order(emptyCheckTable, OrderStatus.OPEN);

        payOrder = order(payTable, OrderStatus.OPEN);
        item(payOrder, OrderItemStatus.PENDING);

        closedOrder = order(paidTable, OrderStatus.CLOSED);
        item(closedOrder, OrderItemStatus.COMPLETED);
        payment = new Payment();
        payment.setOrderId(closedOrder.getOrderId());
        payment.setAmount(new BigDecimal("18.36"));
        payment.setTipAmount(new BigDecimal("4.00"));
        payment.setPaymentMethod("credit_card");
        payment = paymentRepository.save(payment);

        statusLog = new TableStatusLog();
        statusLog.setTableId(busyTable.getTableId());
        statusLog.setNewStatus("occupied");
        statusLog = tableStatusLogRepository.save(statusLog);
    }

    private PrepStation prepStation(String name) {
        PrepStation prepStation = new PrepStation();
        prepStation.setName(name);
        return prepStationRepository.save(prepStation);
    }

    private MenuItem menuItem(String name, String price) {
        MenuItem item = new MenuItem();
        item.setName(name);
        item.setPrice(new BigDecimal(price));
        item.setCategory("Savory");
        item.setPrepStationId(station.getPrepStationId());
        item.setIsActive(true);
        return menuItemRepository.save(item);
    }

    private ModifierGroup modifierGroup(String name) {
        ModifierGroup modifierGroup = new ModifierGroup();
        modifierGroup.setName(name);
        modifierGroup.setIsRequired(false);
        modifierGroup.setMaxSelections(1);
        modifierGroup.setIsActive(true);
        return modifierGroupRepository.save(modifierGroup);
    }

    private Modifier modifier(ModifierGroup modifierGroup, String name, String upcharge) {
        Modifier choice = new Modifier();
        choice.setModifierGroupId(modifierGroup.getModifierGroupId());
        choice.setName(name);
        choice.setPriceAdjustment(new BigDecimal(upcharge));
        choice.setIsActive(true);
        return modifierRepository.save(choice);
    }

    private RestaurantTable table(String number, String status) {
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber(number);
        table.setSeatCount(2);
        table.setStatus(status);
        return tableRepository.save(table);
    }

    private Order order(RestaurantTable table, OrderStatus status) {
        Order order = new Order();
        order.setTableId(table.getTableId());
        order.setStatus(status.getValue());
        if (status == OrderStatus.CLOSED) {
            order.setClosedAt(LocalDateTime.now());
        }
        return orderRepository.save(order);
    }

    private OrderItem item(Order order, OrderItemStatus status) {
        OrderItem item = new OrderItem();
        item.setOrderId(order.getOrderId());
        item.setMenuItemId(menuItem.getMenuItemId());
        item.setQuantity(1);
        item.setPrice(new BigDecimal("17.00"));
        item.setStatus(status.getValue());
        if (status != OrderItemStatus.DRAFT) {                       // Anything past draft has been sent and is locked
            item.setIsLocked(true);
            item.setSentAt(LocalDateTime.now());
        }
        return orderItemRepository.save(item);
    }

    /**
     * A fingerprint of everything in the database: one short text per table that
     * changes if any row in that table is added, removed, or altered.
     *
     * HOW: PostgreSQL turns each row into text (t::text), joins the rows in a fixed
     *      order, and md5() squeezes the result into 32 characters. Two fingerprints
     *      are equal only if every row of every table is the same.
     */
    private Map<String, String> databaseFingerprint() {
        List<String> tableNames = jdbcTemplate.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'",
                String.class);

        Map<String, String> fingerprint = new TreeMap<>();
        for (String tableName : tableNames) {
            fingerprint.put(tableName, jdbcTemplate.queryForObject(
                    "SELECT coalesce(md5(string_agg(t::text, '|' ORDER BY t::text)), 'no rows') FROM \"" + tableName + "\" t",
                    String.class));
        }
        return fingerprint;
    }

    // Every endpoint the backend really has, as "PUT /api/menu-items/{id}/86", with the method that handles it
    private Map<String, HandlerMethod> mappedEndpoints() {
        Map<String, HandlerMethod> mapped = new TreeMap<>();
        for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : handlerMapping.getHandlerMethods().entrySet()) {
            Set<RequestMethod> methods = entry.getKey().getMethodsCondition().getMethods();
            for (String pattern : entry.getKey().getPatternValues()) {
                if (methods.isEmpty()) {
                    mapped.put("GET " + pattern, entry.getValue());             // Mapped for every method (Spring Boot's own /error page): try it as a GET
                }
                for (RequestMethod method : methods) {
                    mapped.put(method.name() + " " + pattern, entry.getValue());
                }
            }
        }
        return mapped;
    }

    // ------------------------------------------------------------------
    // Nobody signed in
    // ------------------------------------------------------------------

    @Test
    void testEveryMappedEndpoint_WithNoSignIn_ShouldAnswer401ExceptSignIn() throws Exception {
        // WHAT: Ask Spring for every endpoint it has, and call each one with no token
        // WHY: This does not use the table above, so it cannot miss an endpoint someone
        //      forgot to add to it. The request is refused before the backend looks at
        //      what it is asking for, so a made-up ID ("1") is enough.

        Map<String, HandlerMethod> mapped = mappedEndpoints();
        assertTrue(mapped.size() > 80, "Expected to find the whole API, found " + mapped.size() + " endpoints");

        List<String> wronglyOpen = new ArrayList<>();
        for (String key : mapped.keySet()) {
            String method = key.substring(0, key.indexOf(' '));
            String path = key.substring(key.indexOf(' ') + 1).replaceAll("\\{[^}]+}", "1");

            MvcResult result = mockMvc.perform(request(HttpMethod.valueOf(method), path)).andReturn();
            int status = result.getResponse().getStatus();

            if (key.equals(SIGN_IN)) {
                assertEquals(400, status, "Sign-in with no PIN is a badly formed request, not a refusal");
            } else if (status != 401) {
                wronglyOpen.add(key + " answered " + status);
            } else {
                // The same body the frontend already reads for a 401
                assertEquals("Sign in to do that.",
                        objectMapper.readTree(result.getResponse().getContentAsString()).get("message").asString(), key);
            }
        }

        assertTrue(wronglyOpen.isEmpty(), "Open to a request with no sign-in:\n  " + String.join("\n  ", wronglyOpen));
    }

    @Test
    void testEveryEndpoint_WithAWellFormedRequestAndNoSignIn_ShouldAnswer401AndChangeNothing() throws Exception {
        // WHAT: Send the real, well-formed request for every endpoint with no token, then
        //       again with a made-up token, and compare the database before and after
        // WHY: The test above shows the door is shut. This one shows nothing got through
        //      it: a request that WOULD have worked for the right person did nothing.

        Map<String, String> before = databaseFingerprint();

        for (Endpoint endpoint : endpoints.values()) {
            mockMvc.perform(endpoint.request().get())
                    .andExpect(result -> assertEquals(401, result.getResponse().getStatus(), endpoint.key()))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value("Sign in to do that."))
                    .andExpect(jsonPath("$.timestamp").exists());

            // A token only counts if the backend handed it out
            mockMvc.perform(endpoint.request().get().header(TOKEN_HEADER, "not-a-real-token"))
                    .andExpect(result -> assertEquals(401, result.getResponse().getStatus(), endpoint.key() + " with a made-up token"))
                    .andExpect(jsonPath("$.message").value("Sign in to do that."));
        }

        assertEquals(before, databaseFingerprint(), "A request with no sign-in changed the database");
    }

    // ------------------------------------------------------------------
    // Signed in: the role matrix
    // ------------------------------------------------------------------

    @Test
    void testEveryEndpoint_ForARoleThatIsNotAllowed_ShouldAnswer403AndChangeNothing() throws Exception {
        // WHAT: Call every endpoint as each role that is not on its list
        // WHY: 403 means "we know who you are, and no". The message names the role so the
        //      person at the screen knows why. And a refusal must be a refusal: nothing saved.

        Map<String, String> before = databaseFingerprint();

        for (Endpoint endpoint : endpoints.values()) {
            for (StaffRole role : StaffRole.values()) {
                if (endpoint.allowed().contains(role)) continue;

                String who = endpoint.key() + " as " + role.getValue();
                mockMvc.perform(endpoint.request().get().header(TOKEN_HEADER, tokens.get(role)))
                        .andExpect(result -> assertEquals(403, result.getResponse().getStatus(), who))
                        .andExpect(jsonPath("$.status").value(403))
                        .andExpect(jsonPath("$.error").value("Forbidden"))
                        .andExpect(jsonPath("$.message").value("Your role (" + role.getValue() + ") is not allowed to do that."));
            }
        }

        assertEquals(before, databaseFingerprint(), "A refused request changed the database");
    }

    @Test
    void testEveryEndpoint_ForARoleThatIsAllowed_ShouldWork() throws Exception {
        // WHAT: Call every endpoint as each role on its list, and expect it to succeed
        // WHY: A rule that refuses everyone would pass every test above. This is also
        //      what shows the requests in the table are real ones: each of them works
        //      for the right person, so the 403s above are about the role and nothing else.

        for (Endpoint endpoint : endpoints.values()) {
            for (StaffRole role : endpoint.allowed()) {
                buildRestaurant();                                   // Put back whatever the last request used up

                String who = endpoint.key() + " as " + role.getValue();
                asking = role;
                mockMvc.perform(endpoint.request().get().header(TOKEN_HEADER, tokens.get(role)))
                        .andExpect(result -> assertEquals(2, result.getResponse().getStatus() / 100,
                                who + " answered " + result.getResponse().getStatus() + " " + result.getResponse().getContentAsString()));

                if (endpoint.key().equals(SIGN_OUT)) {
                    tokens.put(role, signIn(pins.get(role)));        // That really signed them out: sign back in for the rest of the test
                }
                if (endpoint.key().equals(CHANGE_OWN_PIN)) {
                    pins.put(role, newPin);                          // That really changed their PIN: remember it for the next sign-in
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Rules the table cannot show
    // ------------------------------------------------------------------

    @Test
    void testCreateTable_OnTheFloor_ShouldBeManagerOnly() throws Exception {
        // WHAT: POST /api/tables for a table that is NOT a Quick Order
        // WHY: The same endpoint does two jobs. Starting a Quick Order makes a table row,
        //      and anyone who takes orders may do that. Adding a real table to the floor
        //      is a change to the floor, which is the manager's.

        String floorTable = "{\"tableNumber\":\"NEW1\",\"section\":\"Patio\",\"seatCount\":4,\"status\":\"available\"}";

        for (StaffRole role : List.of(StaffRole.SERVER, StaffRole.BARTENDER, StaffRole.HOST, StaffRole.KITCHEN)) {
            mockMvc.perform(json(post("/api/tables"), floorTable).header(TOKEN_HEADER, tokens.get(role)))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.message").value("Your role (" + role.getValue() + ") is not allowed to do that."));
        }
        assertEquals(5, tableRepository.count());                    // Only the five from buildRestaurant()

        mockMvc.perform(json(post("/api/tables"), floorTable).header(TOKEN_HEADER, tokens.get(StaffRole.MANAGER)))
                .andExpect(status().isCreated());
        assertEquals(6, tableRepository.count());
    }

    @Test
    void testCreateTable_NamingAnExistingTable_ShouldMakeANewOneAndLeaveThatTableAlone() throws Exception {
        // WHAT: Start a Quick Order, with the ID of a real table on the floor in the request
        // WHY: A request that carries an ID used to mean "update that row". A server could
        //      turn table W1 into a Quick Order (taking it off the Floor Map) or move it,
        //      which only a manager may do. Creating always creates.

        mockMvc.perform(json(post("/api/tables"),
                "{\"tableId\":" + busyTable.getTableId() + ",\"tableNumber\":\"QO77\",\"section\":\"Quick Orders\","
                        + "\"seatCount\":0,\"status\":\"available\",\"isQuickOrder\":true}")
                .header(TOKEN_HEADER, tokens.get(StaffRole.SERVER)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tableId").value(org.hamcrest.Matchers.not(busyTable.getTableId().intValue())));

        RestaurantTable untouched = tableRepository.findById(busyTable.getTableId()).orElseThrow();
        assertEquals("W1", untouched.getTableNumber());
        assertEquals("occupied", untouched.getStatus());
        assertNotEquals(Boolean.TRUE, untouched.getIsQuickOrder());
        assertEquals(6, tableRepository.count());                    // The five from buildRestaurant(), plus the new Quick Order
    }

    @Test
    void testTheRemovedEndpoints_ShouldNoLongerExist() throws Exception {
        // WHAT: Call the four endpoints that step A3b deleted, as a manager
        // WHY: Nothing in the app used them, and each was a way around the rules:
        //        PUT /api/orders/{id}/close   closed a check with no payment
        //        PUT /api/orders/{id}/totals  wrote totals worked out from a tax rate the caller chose
        //        PUT /api/payments/{id}       rewrote a payment after the fact
        //        DELETE /api/payments/{id}    removed one
        //      Refunds and voids come back, done properly, in step B5 of the plan.
        //      404 = no such address. 405 = the address exists, but not for that method
        //      (GET /api/payments/{id} is still there).

        String manager = tokens.get(StaffRole.MANAGER);
        Map<String, String> before = databaseFingerprint();

        mockMvc.perform(put("/api/orders/" + openOrder.getOrderId() + "/close").header(TOKEN_HEADER, manager))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/orders/" + openOrder.getOrderId() + "/totals").header(TOKEN_HEADER, manager)
                .param("subtotal", "10.00").param("taxRate", "0.00"))
                .andExpect(status().isNotFound());
        mockMvc.perform(json(put("/api/payments/" + payment.getPaymentId()), "{\"amount\":0.01,\"paymentMethod\":\"cash\"}").header(TOKEN_HEADER, manager))
                .andExpect(status().isMethodNotAllowed());
        mockMvc.perform(delete("/api/payments/" + payment.getPaymentId()).header(TOKEN_HEADER, manager))
                .andExpect(status().isMethodNotAllowed());

        assertEquals(before, databaseFingerprint());
    }

    // ------------------------------------------------------------------
    // Two guards on the set-up itself
    // ------------------------------------------------------------------

    @Test
    void testTheTableInThisTest_ShouldListExactlyTheEndpointsTheBackendHas() {
        // WHAT: Compare the table in setUp() with what Spring says is mapped
        // WHY: The role tests only check what is in the table. A new endpoint that is not
        //      in it would go unchecked, so this fails until it is added (with its roles).
        //      (This and the next test are the two that look at how the backend is built.)

        Set<String> mapped = new TreeSet<>(mappedEndpoints().keySet());
        mapped.remove(SIGN_IN);                                      // Open on purpose
        mapped.removeIf(key -> !key.contains(" /api/"));             // Spring Boot's own /error page is not part of the API

        assertEquals(mapped, new TreeSet<>(endpoints.keySet()),
                "The table in this test and the backend's endpoints do not match");
    }

    @Test
    void testEveryEndpointThatChangesData_ShouldCarryARoleRule() {
        // WHAT: Check that every POST, PUT, and DELETE endpoint has @PreAuthorize on it
        // WHY: Without a rule, an endpoint is open to anyone who is signed in. That is
        //      right for reading the menu and wrong for changing it. Signing in and out
        //      are the two that need no role and carry no rule. An endpoint that changes
        //      something and IS meant for every role says so with Permissions.ANYONE_SIGNED_IN
        //      (choosing your own PIN), so a missing rule still means "someone forgot".

        List<String> missing = new ArrayList<>();
        mappedEndpoints().forEach((key, handler) -> {
            boolean changesData = !key.startsWith("GET ");
            boolean hasRule = handler.hasMethodAnnotation(PreAuthorize.class);
            if (changesData && !hasRule && !key.equals(SIGN_IN) && !key.equals(SIGN_OUT)) {
                missing.add(key);
            }
        });

        assertTrue(missing.isEmpty(), "No role rule (@PreAuthorize) on:\n  " + String.join("\n  ", missing));
    }
}
