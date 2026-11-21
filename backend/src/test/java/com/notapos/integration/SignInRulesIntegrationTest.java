package com.notapos.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.notapos.dto.StaffCreateRequest;
import com.notapos.entity.MenuItem;
import com.notapos.entity.RestaurantTable;
import com.notapos.enums.StaffRole;
import com.notapos.repository.MenuItemRepository;
import com.notapos.repository.OrderItemModifierRepository;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.PaymentRepository;
import com.notapos.repository.StaffRepository;
import com.notapos.repository.TableRepository;
import com.notapos.security.StaffTokenFilter;
import com.notapos.service.StaffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for who may do what, asked from the outside.
 *
 * WHAT: One real staff member per role signs in with a real PIN and gets a
 *       real token. Each restricted endpoint is then called by nobody, by a
 *       made-up token, and by every role, and the answer is checked.
 * WHY:  The controller tests check these rules one controller at a time, with
 *       the services faked. This test checks all of them through the whole
 *       backend, and it knows nothing about HOW the backend decides. It was
 *       written against the home-built sign-in check and passed, and then had
 *       to keep passing, unchanged, when sign-in moved onto Spring Security.
 *       It is the proof that the move changed nothing a screen can see.
 *
 * The table of rules (RULES below) is the same list CLAUDE.md gives under
 * "Restricted on the backend". If a rule is added or changed, add it there.
 *
 * @author CJ
 */
class SignInRulesIntegrationTest extends BaseIntegrationTest {

    private static final String TOKEN_HEADER = "X-Staff-Token";
    private static final String ALLOWED_ORIGIN = "http://localhost:3000";       // notta.cors.allowed-origins in application.properties

    @Autowired private StaffService staffService;
    @Autowired private StaffRepository staffRepository;
    @Autowired private TableRepository tableRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private OrderItemRepository orderItemRepository;
    @Autowired private OrderItemModifierRepository orderItemModifierRepository;
    @Autowired private PaymentRepository paymentRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ApplicationContext applicationContext;
    @Autowired @Qualifier("requestMappingHandlerMapping") private RequestMappingHandlerMapping handlerMapping;     // Every endpoint the backend has

    // A token for one signed-in person of each role, and who they are
    private final Map<StaffRole, String> tokens = new EnumMap<>(StaffRole.class);
    private final Map<StaffRole, Long> staffIds = new EnumMap<>(StaffRole.class);

    private Long someoneElseId;         // A staff member the tests change (so nobody's own session is touched)
    private RestaurantTable table;
    private MenuItem menuItem;
    private int nextPin = 5000;         // PINs for staff created during a test: each must be unused

    // One restricted endpoint: what to call it, how to build a valid request, and who is allowed
    private record Rule(String name, Supplier<MockHttpServletRequestBuilder> request, Set<StaffRole> allowed) {
    }

    private final Map<String, Rule> rules = new LinkedHashMap<>();

    @BeforeEach
    void setUp() throws Exception {
        // Clean every table these tests touch (nothing rolls back by itself here)
        orderItemModifierRepository.deleteAll();
        orderItemRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        menuItemRepository.deleteAll();
        tableRepository.deleteAll();
        staffRepository.deleteAll();

        // One person per role, each with their own PIN, signed in the way the PIN pad does it
        int pin = 2001;
        for (StaffRole role : StaffRole.values()) {
            String pinText = String.valueOf(pin++);
            Long id = staffService.createStaff(new StaffCreateRequest("The " + role.getValue(), role.getValue(), pinText)).staffId();
            staffIds.put(role, id);
            tokens.put(role, signIn(pinText));
        }
        someoneElseId = staffService.createStaff(new StaffCreateRequest("Someone Else", "server", "2999")).staffId();

        table = new RestaurantTable();
        table.setTableNumber("W1");
        table.setSeatCount(2);
        table.setStatus("available");
        table = tableRepository.save(table);

        menuItem = new MenuItem();
        menuItem.setName("Chicken Cutty");
        menuItem.setPrice(new BigDecimal("17.00"));
        menuItem.setCategory("Savory");
        menuItem.setIsActive(true);
        menuItem = menuItemRepository.save(menuItem);

        // ------------------------------------------------------------------
        // The seven restricted endpoints and who may use each
        // ------------------------------------------------------------------
        Set<StaffRole> managerOnly = EnumSet.of(StaffRole.MANAGER);
        Set<StaffRole> whoKnowsWhatRanOut = EnumSet.of(StaffRole.MANAGER, StaffRole.KITCHEN, StaffRole.BARTENDER);

        addRule("list staff", managerOnly, () -> get("/api/staff"));
        addRule("add a staff member", managerOnly, () -> post("/api/staff")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"New Person\",\"role\":\"server\",\"pin\":\"" + (nextPin++) + "\"}"));
        addRule("change a staff member", managerOnly, () -> put("/api/staff/" + someoneElseId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Renamed\"}"));
        addRule("Financials report", managerOnly, () -> get("/api/reports/financials")
                .param("from", "2026-11-18T00:00:00").param("to", "2026-11-19T00:00:00"));
        addRule("save the floor layout", managerOnly, () -> put("/api/tables/layout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tables\":[{\"tableId\":" + table.getTableId() + ",\"x\":100,\"y\":120,\"rotation\":0}]}"));
        addRule("86 an item", whoKnowsWhatRanOut, () -> put("/api/menu-items/" + menuItem.getMenuItemId() + "/86"));
        addRule("un-86 an item", whoKnowsWhatRanOut, () -> put("/api/menu-items/" + menuItem.getMenuItemId() + "/un-86"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void addRule(String name, Set<StaffRole> allowed, Supplier<MockHttpServletRequestBuilder> request) {
        rules.put(name, new Rule(name, request, allowed));
    }

    // Sign in with a PIN through the real endpoint and return the token it hands back
    private String signIn(String pin) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/staff/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"" + pin + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
    }

    // ------------------------------------------------------------------
    // The seven rules
    // ------------------------------------------------------------------

    @Test
    void testEveryRestrictedEndpoint_WithNoSignIn_ShouldAnswer401() throws Exception {
        // WHAT: Call each restricted endpoint with no token at all
        // WHY: 401 means "we do not know who you are". The frontend answers a 401 by
        //      showing the PIN pad, and shows this exact message.

        for (Rule rule : rules.values()) {
            mockMvc.perform(rule.request().get())
                    .andExpect(result -> assertEquals(401, result.getResponse().getStatus(), rule.name()))
                    .andExpect(jsonPath("$.status").value(401))
                    .andExpect(jsonPath("$.error").value("Unauthorized"))
                    .andExpect(jsonPath("$.message").value("Sign in to do that."))
                    .andExpect(jsonPath("$.timestamp").exists());
        }
    }

    @Test
    void testEveryRestrictedEndpoint_WithATokenNobodyWasGiven_ShouldAnswer401() throws Exception {
        // WHAT: Call each restricted endpoint with a made-up token
        // WHY: A token only counts if the backend handed it out. A stale one (the backend
        //      was restarted, the person was signed out) is the same as none.

        for (Rule rule : rules.values()) {
            mockMvc.perform(rule.request().get().header(TOKEN_HEADER, "not-a-real-token"))
                    .andExpect(result -> assertEquals(401, result.getResponse().getStatus(), rule.name()))
                    .andExpect(jsonPath("$.message").value("Sign in to do that."));
        }
    }

    @Test
    void testEveryRestrictedEndpoint_ForARoleThatIsNotAllowed_ShouldAnswer403AndNameTheRole() throws Exception {
        // WHAT: Call each restricted endpoint as every role that is not on its list
        // WHY: 403 means "we know who you are, and no". The message names the role so the
        //      person at the screen knows why.

        for (Rule rule : rules.values()) {
            for (StaffRole role : StaffRole.values()) {
                if (rule.allowed().contains(role)) continue;

                String who = rule.name() + " as " + role.getValue();
                mockMvc.perform(rule.request().get().header(TOKEN_HEADER, tokens.get(role)))
                        .andExpect(result -> assertEquals(403, result.getResponse().getStatus(), who))
                        .andExpect(jsonPath("$.status").value(403))
                        .andExpect(jsonPath("$.error").value("Forbidden"))
                        .andExpect(jsonPath("$.message").value("Your role (" + role.getValue() + ") is not allowed to do that."));
            }
        }
    }

    @Test
    void testEveryRestrictedEndpoint_ForARoleThatIsAllowed_ShouldGoThrough() throws Exception {
        // WHAT: Call each restricted endpoint as every role on its list
        // WHY: A rule that refuses everyone would pass the three tests above

        for (Rule rule : rules.values()) {
            for (StaffRole role : rule.allowed()) {
                String who = rule.name() + " as " + role.getValue();
                mockMvc.perform(rule.request().get().header(TOKEN_HEADER, tokens.get(role)))
                        .andExpect(result -> assertTrue(result.getResponse().getStatus() / 100 == 2,
                                who + " answered " + result.getResponse().getStatus()));
            }
        }
    }

    // ------------------------------------------------------------------
    // Everything else is open (until step A3b of the plan locks it)
    // ------------------------------------------------------------------

    @Test
    void testAnEndpointWithNoRule_ShouldAnswerWithOrWithoutAToken() throws Exception {
        // WHAT: Call an unrestricted endpoint with no token, a made-up token, and a real one
        // WHY: A bad token on an open endpoint is treated as "nobody", not refused. The order
        //      screen keeps working for the moment between a session ending and the next request
        //      that needs one.

        mockMvc.perform(get("/api/tables")).andExpect(status().isOk());
        mockMvc.perform(get("/api/tables").header(TOKEN_HEADER, "not-a-real-token")).andExpect(status().isOk());
        mockMvc.perform(get("/api/tables").header(TOKEN_HEADER, tokens.get(StaffRole.HOST))).andExpect(status().isOk());

        // A request that changes data, with no token and nothing else special about it
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":" + table.getTableId() + ",\"orderType\":\"dine_in\"}"))
                .andExpect(status().isCreated());
    }

    // ------------------------------------------------------------------
    // Who am I, and signing out
    // ------------------------------------------------------------------

    @Test
    void testWhoAmI_ShouldAnswerWithThePersonBehindTheToken() throws Exception {
        // WHAT: Test GET /api/staff/me
        // WHY: When the page is reloaded the frontend asks who its saved token belongs to

        mockMvc.perform(get("/api/staff/me").header(TOKEN_HEADER, tokens.get(StaffRole.KITCHEN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffId").value(staffIds.get(StaffRole.KITCHEN)))
                .andExpect(jsonPath("$.name").value("The kitchen"))
                .andExpect(jsonPath("$.role").value("kitchen"));

        mockMvc.perform(get("/api/staff/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sign in to continue."));
        mockMvc.perform(get("/api/staff/me").header(TOKEN_HEADER, "not-a-real-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testSignOut_ShouldStopTheTokenWorking() throws Exception {
        // WHAT: Sign out, then use the same token again
        // WHY: On a shared terminal the next person must not act as the last one

        String token = tokens.get(StaffRole.MANAGER);
        mockMvc.perform(get("/api/staff").header(TOKEN_HEADER, token)).andExpect(status().isOk());

        mockMvc.perform(post("/api/staff/sign-out").header(TOKEN_HEADER, token)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/staff").header(TOKEN_HEADER, token)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/staff/me").header(TOKEN_HEADER, token)).andExpect(status().isUnauthorized());
        // Signing out twice, or with no token, is not an error
        mockMvc.perform(post("/api/staff/sign-out").header(TOKEN_HEADER, token)).andExpect(status().isNoContent());
        mockMvc.perform(post("/api/staff/sign-out")).andExpect(status().isNoContent());
    }

    @Test
    void testChangingSomeonesRole_ShouldSignThemOutEverywhere() throws Exception {
        // WHAT: A manager changes the kitchen person's role while they are signed in
        // WHY: Their old token was issued for the old role. It must stop working, so the
        //      next thing they do is sign in again and get the new role's permissions.

        String kitchenToken = tokens.get(StaffRole.KITCHEN);
        mockMvc.perform(put("/api/menu-items/" + menuItem.getMenuItemId() + "/86").header(TOKEN_HEADER, kitchenToken))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/staff/" + staffIds.get(StaffRole.KITCHEN))
                .header(TOKEN_HEADER, tokens.get(StaffRole.MANAGER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"host\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/menu-items/" + menuItem.getMenuItemId() + "/un-86").header(TOKEN_HEADER, kitchenToken))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // A new order is stamped with whoever is signed in
    // ------------------------------------------------------------------

    @Test
    void testOpenOrder_ShouldBeStampedWithTheSignedInPersonNotTheNameInTheRequest() throws Exception {
        // WHAT: Open an order while signed in, sending someone else's name in the request
        // WHY: The order belongs to whoever is at the screen. The name a request carries is
        //      only used when nobody is signed in.

        mockMvc.perform(post("/api/orders")
                .header(TOKEN_HEADER, tokens.get(StaffRole.BARTENDER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":" + table.getTableId() + ",\"orderType\":\"dine_in\",\"serverName\":\"Not Me\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serverName").value("The bartender"))
                .andExpect(jsonPath("$.staffId").value(staffIds.get(StaffRole.BARTENDER)));
    }

    @Test
    void testOpenOrder_WithNobodySignedIn_ShouldKeepTheNameInTheRequest() throws Exception {
        // WHAT: Open an order with no token
        // WHY: Still allowed until step A3b of the plan; the request's own name is all there is

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":" + table.getTableId() + ",\"orderType\":\"dine_in\",\"serverName\":\"CJ\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serverName").value("CJ"))
                .andExpect(jsonPath("$.staffId").doesNotExist());
    }

    // ------------------------------------------------------------------
    // Which check comes first: "who are you" or "is the request well formed"
    // ------------------------------------------------------------------

    @Test
    void testARequestThatIsBadlyFormedAndNotAllowed_ShouldBeToldItIsBadlyFormed() throws Exception {
        // WHAT: Send a request that is wrong twice: the person may not make it, and its body
        //       (or its parameters) would not pass validation either
        // WHY: This pins down the ONE answer that changed when sign-in moved onto Spring
        //      Security. The role rules sit on the controller method (@PreAuthorize). Spring
        //      reads and validates the request before it calls the method, so a badly formed
        //      request is answered 400 before the rule is reached. Under the home-built check
        //      the order was the other way round, and these answered 401 and 403.
        //
        //      Nothing is let through: the same people sending a WELL formed request are
        //      refused, which the tests at the top of this class prove for every endpoint.
        //      What an outsider can learn this way is the wording of validation messages.
        //      Step A3b of the plan refuses requests with no sign-in before they get this
        //      far, which puts the 401 back in front for anyone not signed in.

        String badStaff = "{\"name\":\"\",\"role\":\"\",\"pin\":\"12\"}";

        // Nobody signed in
        mockMvc.perform(post("/api/staff").contentType(MediaType.APPLICATION_JSON).content(badStaff))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/reports/financials"))                             // No from or to
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/tables/layout").contentType(MediaType.APPLICATION_JSON).content("{\"tables\":[]}"))
                .andExpect(status().isBadRequest());

        // Signed in with a role that is not allowed
        String serverToken = tokens.get(StaffRole.SERVER);
        mockMvc.perform(post("/api/staff").header(TOKEN_HEADER, serverToken).contentType(MediaType.APPLICATION_JSON).content(badStaff))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/reports/financials").header(TOKEN_HEADER, serverToken))
                .andExpect(status().isBadRequest());

        // Someone who IS allowed gets the same 400, as before
        mockMvc.perform(post("/api/staff").header(TOKEN_HEADER, tokens.get(StaffRole.MANAGER))
                .contentType(MediaType.APPLICATION_JSON).content(badStaff))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));

        // And none of the badly formed requests did anything
        assertEquals(StaffRole.values().length + 1, staffRepository.count());       // One per role, plus "Someone Else"
    }

    // ------------------------------------------------------------------
    // The browser's cross-origin checks (CORS) still work around sign-in
    // ------------------------------------------------------------------

    @Test
    void testPreflight_ShouldBeAnsweredWithoutAToken() throws Exception {
        // WHAT: Send the "preflight" a browser sends before a cross-origin request
        // WHY: Before a PUT, or any request with the X-Staff-Token header, a browser first
        //      asks "may this page do that?" with an OPTIONS request. That question never
        //      carries the token. If sign-in were checked first, the browser would be told
        //      no, and the real request would never be sent: every screen would break.

        mockMvc.perform(options("/api/staff")
                .header("Origin", ALLOWED_ORIGIN)
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type,x-staff-token"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Headers", "content-type, x-staff-token"));

        // From an address that is not allowed, the answer is no
        mockMvc.perform(options("/api/staff")
                .header("Origin", "http://somewhere-else.example")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testARefusal_ShouldStillCarryTheHeaderThatLetsThePageReadIt() throws Exception {
        // WHAT: A refused cross-origin request (401 and 403) from the frontend's address
        // WHY: A browser hides any answer that lacks Access-Control-Allow-Origin from the
        //      page. Without it the frontend would see a network error instead of the 401,
        //      and would not know to show the PIN pad.

        mockMvc.perform(get("/api/staff").header("Origin", ALLOWED_ORIGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));

        mockMvc.perform(get("/api/staff").header("Origin", ALLOWED_ORIGIN).header(TOKEN_HEADER, tokens.get(StaffRole.SERVER)))
                .andExpect(status().isForbidden())
                .andExpect(header().string("Access-Control-Allow-Origin", ALLOWED_ORIGIN));
    }

    // ------------------------------------------------------------------
    // Things this app's sign-in must NOT have
    // ------------------------------------------------------------------

    @Test
    void testARefusal_ShouldNotMakeTheBrowserAskForAPassword() throws Exception {
        // WHAT: Check the headers on a 401
        // WHY: A "WWW-Authenticate" header on a 401 makes a browser pop up its own
        //      username-and-password box. Sign-in here is the PIN pad and nothing else.

        mockMvc.perform(get("/api/staff"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"));
    }

    @Test
    void testSignIn_ShouldNotHandOutACookie() throws Exception {
        // WHAT: Check that no answer sets a cookie, signed in or not
        // WHY: The token travels in a header the frontend adds itself. A browser attaches
        //      cookies to requests on its own, including ones a hostile page tricks it
        //      into sending; a header it does not. Keeping cookies out is what makes it
        //      safe for this backend to do without CSRF tokens.

        mockMvc.perform(post("/api/staff/sign-in").contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"2001\"}"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
        mockMvc.perform(get("/api/staff").header(TOKEN_HEADER, tokens.get(StaffRole.MANAGER)))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
        mockMvc.perform(get("/api/tables"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void testThereIsNoLoginPageOrLogoutAddress() throws Exception {
        // WHAT: Ask for the addresses a ready-made login page and logout would live at
        // WHY: The only ways in and out are POST /api/staff/sign-in and /sign-out

        mockMvc.perform(get("/login")).andExpect(status().isNotFound());
        mockMvc.perform(post("/logout")).andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // Two guards on the set-up itself
    // ------------------------------------------------------------------

    @Test
    void testTheRulesTableInThisTest_ShouldCoverEveryEndpointThatHasARule() {
        // WHAT: Count the controller methods that carry a @PreAuthorize rule
        // WHY: The tests above only check the endpoints listed in setUp(). If someone adds a
        //      rule to another endpoint and forgets this table, that rule would go untested
        //      here. This fails until the table is brought up to date.
        //      (This is the one test in this class that looks at how the backend is built.)

        long endpointsWithARule = handlerMapping.getHandlerMethods().values().stream()
                .filter(method -> method.hasMethodAnnotation(PreAuthorize.class)
                        || method.getBeanType().isAnnotationPresent(PreAuthorize.class))
                .count();

        assertEquals(rules.size(), endpointsWithARule,
                "An endpoint has a @PreAuthorize rule that is not in this test's table (or the other way round)");
    }

    @Test
    void testTheTokenFilter_ShouldNotBeASpringBean() {
        // WHAT: Check that StaffTokenFilter is not registered as a bean
        // WHY: Spring Boot adds every filter bean to the web server by itself. As a bean the
        //      filter would run twice per request: once there, outside Spring Security, and
        //      once inside its chain. SecurityConfig creates it with "new" for that reason.
        //      This catches a well-meant @Component being added to it later.

        assertTrue(applicationContext.getBeansOfType(StaffTokenFilter.class).isEmpty());
    }
}
