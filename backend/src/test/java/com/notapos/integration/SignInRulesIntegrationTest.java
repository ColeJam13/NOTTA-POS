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
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for how signing in behaves, asked from the outside.
 *
 * WHAT: One real staff member per role signs in with a real PIN and gets a
 *       real token. The tests then check the mechanics around that token:
 *       what a request with no sign-in is told, who-am-I, signing out, a role
 *       change ending sessions, whose name goes on an order, the browser's
 *       cross-origin checks (CORS), and the things this sign-in must NOT have
 *       (a password prompt, a cookie, a login page).
 * WHY:  These are answers the frontend depends on, and they come from the
 *       whole backend working together: the filter chain, the controllers, and
 *       the error handling. This test knows nothing about HOW the backend
 *       decides. Most of it was written against the home-built sign-in check
 *       and had to keep passing when sign-in moved onto Spring Security.
 *
 * WHO MAY DO WHAT, endpoint by endpoint and role by role, is in
 * RoleMatrixIntegrationTest.
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

    // A token for one signed-in person of each role, and who they are
    private final Map<StaffRole, String> tokens = new EnumMap<>(StaffRole.class);
    private final Map<StaffRole, Long> staffIds = new EnumMap<>(StaffRole.class);

    private RestaurantTable table;
    private MenuItem menuItem;

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
        staffService.createStaff(new StaffCreateRequest("Someone Else", "server", "2999"));       // Someone who never signs in

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

    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

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
    // Every request needs a signed-in person
    // ------------------------------------------------------------------

    @Test
    void testARequestWithNoSignIn_ShouldBeAnswered401InTheUsualErrorShape() throws Exception {
        // WHAT: Call an ordinary endpoint with no token, with a made-up token, and with a real one
        // WHY: 401 means "we do not know who you are". The frontend answers a 401 by showing
        //      the PIN pad. A token only counts if the backend handed it out: a stale one
        //      (the backend was restarted, the person was signed out) is the same as none.
        //      Until step A3b of the plan, this endpoint answered all three.

        mockMvc.perform(get("/api/tables"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Sign in to do that."))
                .andExpect(jsonPath("$.timestamp").exists());
        mockMvc.perform(get("/api/tables").header(TOKEN_HEADER, "not-a-real-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sign in to do that."));

        // An endpoint with no role rule is for anyone who IS signed in
        mockMvc.perform(get("/api/tables").header(TOKEN_HEADER, tokens.get(StaffRole.HOST))).andExpect(status().isOk());
        mockMvc.perform(get("/api/tables").header(TOKEN_HEADER, tokens.get(StaffRole.KITCHEN))).andExpect(status().isOk());
    }

    @Test
    void testSignIn_ShouldBeTheOneThingOpenToARequestWithNoSignIn() throws Exception {
        // WHAT: Sign in with a right PIN and a wrong one, with no token on either request
        // WHY: You cannot be asked to sign in before signing in. A wrong PIN is also a 401,
        //      but with its own message, which the PIN pad shows.

        mockMvc.perform(post("/api/staff/sign-in").contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"2001\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
        mockMvc.perform(post("/api/staff/sign-in").contentType(MediaType.APPLICATION_JSON).content("{\"pin\":\"0000\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("PIN not recognized."));

        // Only POST is open. Another method on the same address is refused like anything else.
        mockMvc.perform(get("/api/staff/sign-in")).andExpect(status().isUnauthorized());
    }

    @Test
    void testAnAddressThatDoesNotExist_ShouldBe401ForNobodyAnd404ForStaff() throws Exception {
        // WHAT: Ask for an address the backend does not have
        // WHY: A request with no sign-in is refused before the backend works out whether the
        //      address exists, so an outsider cannot map the API by trying addresses. A
        //      signed-in person gets the honest answer, 404, not a misleading "sign in".

        mockMvc.perform(get("/api/no-such-thing")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/somewhere/else")).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/no-such-thing").header(TOKEN_HEADER, tokens.get(StaffRole.SERVER)))
                .andExpect(status().isNotFound());
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
                .andExpect(jsonPath("$.message").value("Sign in to do that."));
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
        // Signing out twice, or with no token: there is no session left to end, so it is
        // answered like any other request from nobody. (The frontend forgets the token
        // whatever the answer is.)
        mockMvc.perform(post("/api/staff/sign-out").header(TOKEN_HEADER, token)).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/staff/sign-out")).andExpect(status().isUnauthorized());
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
        // WHY: The order belongs to whoever is at the screen. A name or staff ID the request
        //      carries is ignored: otherwise a check could be opened in someone else's name.

        mockMvc.perform(post("/api/orders")
                .header(TOKEN_HEADER, tokens.get(StaffRole.BARTENDER))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":" + table.getTableId() + ",\"orderType\":\"dine_in\",\"serverName\":\"Not Me\",\"staffId\":"
                        + staffIds.get(StaffRole.MANAGER) + "}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.serverName").value("The bartender"))
                .andExpect(jsonPath("$.staffId").value(staffIds.get(StaffRole.BARTENDER)));
    }

    @Test
    void testOpenOrder_WithNobodySignedIn_ShouldBeRefusedAndOpenNothing() throws Exception {
        // WHAT: Open an order with no token
        // WHY: Every order belongs to a signed-in person. (Until step A3b of the plan this
        //      was allowed, and the order kept whatever name the request sent.)

        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"tableId\":" + table.getTableId() + ",\"orderType\":\"dine_in\",\"serverName\":\"CJ\"}"))
                .andExpect(status().isUnauthorized());

        assertEquals(0, orderRepository.count());
        assertEquals("available", tableRepository.findById(table.getTableId()).orElseThrow().getStatus());
    }

    // ------------------------------------------------------------------
    // Which check comes first: "who are you" or "is the request well formed"
    // ------------------------------------------------------------------

    @Test
    void testARequestThatIsBadlyFormedAndNotAllowed_ShouldBe401ForNobodyAnd400ForTheWrongRole() throws Exception {
        // WHAT: Send a request that is wrong twice: the person may not make it, and its body
        //       (or its parameters) would not pass validation either
        // WHY: Two checks run at two different moments.
        //
        //      "Are you signed in?" is asked in the filter chain, before the request is read.
        //      So nobody gets 401 whatever the request contains, and learns nothing about
        //      what a valid one looks like.
        //
        //      "May your ROLE do this?" is asked at the controller method (@PreAuthorize).
        //      Spring reads and validates the request before it calls the method, so a
        //      signed-in person with the wrong role AND a badly formed request is told 400.
        //      Nothing is let through: the same person sending a WELL formed request is
        //      refused, which RoleMatrixIntegrationTest proves for every endpoint. What they
        //      can learn this way is the wording of a validation message.

        String badStaff = "{\"name\":\"\",\"role\":\"\",\"pin\":\"12\"}";

        // Nobody signed in: 401, before the request is looked at
        mockMvc.perform(post("/api/staff").contentType(MediaType.APPLICATION_JSON).content(badStaff))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/reports/financials"))                             // No from or to
                .andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/tables/layout").contentType(MediaType.APPLICATION_JSON).content("{\"tables\":[]}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("this is not JSON"))
                .andExpect(status().isUnauthorized());

        // Signed in with a role that is not allowed: 400
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
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void testThereIsNoLoginPageOrLogoutAddress() throws Exception {
        // WHAT: Ask for the addresses a ready-made login page and logout would live at
        // WHY: The only ways in and out are POST /api/staff/sign-in and /sign-out

        // For a signed-in person they simply do not exist
        String token = tokens.get(StaffRole.MANAGER);
        mockMvc.perform(get("/login").header(TOKEN_HEADER, token)).andExpect(status().isNotFound());
        mockMvc.perform(post("/logout").header(TOKEN_HEADER, token)).andExpect(status().isNotFound());

        // For nobody they are refused like every other address: this app's own 401, not a
        // page with a form on it and not a redirect to one
        mockMvc.perform(get("/login"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sign in to do that."))
                .andExpect(header().doesNotExist("Location"));
        mockMvc.perform(post("/logout")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // A guard on the set-up itself
    // ------------------------------------------------------------------

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
