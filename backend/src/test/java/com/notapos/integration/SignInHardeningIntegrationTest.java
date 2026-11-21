package com.notapos.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notapos.dto.StaffCreateRequest;
import com.notapos.repository.StaffRepository;
import com.notapos.service.StaffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for step A4 of the Phase 2 plan: sign-in hardening.
 *
 * WHAT: Real PINs sent to the real backend, from more than one device, and a
 *       look at what the database holds afterwards.
 * WHY:  Each of these was a defect found by reading the sign-in code:
 *         - five wrong PINs on ANY device paused sign-in for EVERY device
 *         - sessions lived in the backend's memory, so a restart signed the
 *           whole floor out
 *         - every new database had a manager with PIN 1234, and nothing made
 *           anyone change it
 *       The first three tests were written before the fix and failed.
 *
 * "A DEVICE" here is the address a request comes from. MockMvc lets a test say
 * what that address is (see from()).
 *
 * How the pauses grow, and the counting rules themselves, are tested without
 * the web layer in SignInGuardTest, where the clock can be moved by hand.
 *
 * @author CJ
 */
class SignInHardeningIntegrationTest extends BaseIntegrationTest {

    private static final String TOKEN_HEADER = "X-Staff-Token";

    // Two terminals, and something else on the network
    private static final String BAR_TERMINAL = "192.168.1.21";
    private static final String PATIO_TERMINAL = "192.168.1.22";

    @Autowired private StaffService staffService;
    @Autowired private StaffRepository staffRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        staffRepository.deleteAll();
        staffService.createStaff(new StaffCreateRequest("Morgan", "manager", "2222"));
        staffService.createStaff(new StaffCreateRequest("Sarah", "server", "1111"));
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    // Make a request arrive from a given address, the way it would from that terminal
    private static RequestPostProcessor from(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private static MockHttpServletRequestBuilder signInWith(String pin) {
        return post("/api/staff/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"" + pin + "\"}");
    }

    // Sign in through the real endpoint and return the whole answer
    private JsonNode signIn(String pin, String address) throws Exception {
        MvcResult result = mockMvc.perform(signInWith(pin).with(from(address)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String sha256Hex(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    // ------------------------------------------------------------------
    // Wrong PINs are counted per device
    // ------------------------------------------------------------------

    @Test
    void testWrongPinsOnOneDevice_ShouldPauseThatDeviceAndNoOther() throws Exception {
        // WHAT: Five wrong PINs at the bar terminal, then the right PIN at each terminal
        // WHY: The count used to be one number for the whole restaurant. One person
        //      fumbling at the bar, or anything on the network sending five bad PINs,
        //      stopped every terminal from signing anyone in.

        // Given - five wrong PINs at the bar
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(signInWith("9999").with(from(BAR_TERMINAL)))
                    .andExpect(status().isUnauthorized());
        }

        // Then - the patio terminal carries on as if nothing happened
        assertEquals("Sarah", signIn("1111", PATIO_TERMINAL).get("staff").get("name").asText());

        // And - the bar terminal is paused, even for a right PIN, and is told for how long
        mockMvc.perform(signInWith("1111").with(from(BAR_TERMINAL)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many wrong PINs on this device. Try again in 30 seconds."));
    }

    // ------------------------------------------------------------------
    // Sessions are kept in the database
    // ------------------------------------------------------------------

    @Test
    void testSignIn_ShouldBeRememberedInTheDatabaseAsAHashOfTheToken() throws Exception {
        // WHAT: Sign in, then read the staff_sessions table directly
        // WHY: Two things. (1) The session must be in the database, or a backend restart
        //      signs everyone out. (2) What is stored must not be the token itself:
        //      anyone who got a copy of the database (a backup, a dump) could otherwise
        //      send a stored token and act as that person. A hash can be checked against
        //      a token but cannot be turned back into one.

        // When
        String token = signIn("2222", BAR_TERMINAL).get("token").asText();

        // Then - one row, for Morgan
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM staff_sessions");
        assertEquals(1, rows.size());

        // And - it holds the SHA-256 hash of the token, and the token appears nowhere in the row
        Map<String, Object> row = rows.get(0);
        assertEquals(sha256Hex(token), row.get("token_hash"));
        row.forEach((column, value) ->
                assertFalse(String.valueOf(value).contains(token), "The token itself is stored in column " + column));

        // And - the token still works, because the backend hashes what it is sent and looks that up
        mockMvc.perform(get("/api/staff/me").header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Morgan"));
    }

    // ------------------------------------------------------------------
    // The starter PIN
    // ------------------------------------------------------------------

    @Test
    void testTheStarterPin_ShouldBeRefusedAsANewPin() throws Exception {
        // WHAT: Try to give someone the PIN 1234, when adding them and when changing them
        // WHY: 1234 is the PIN every new database starts with, so it is the first one
        //      anybody would guess. Nobody may hold it.

        String morgan = signIn("2222", BAR_TERMINAL).get("token").asText();
        Long sarahId = staffRepository.findAll().stream()
                .filter(staff -> staff.getName().equals("Sarah")).findFirst().orElseThrow().getStaffId();

        // Adding someone with it
        mockMvc.perform(post("/api/staff").header(TOKEN_HEADER, morgan)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alex\",\"role\":\"server\",\"pin\":\"1234\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("1234 is the starter PIN and cannot be used. Pick a different one."));
        assertEquals(2, staffRepository.count());

        // Changing someone to it
        mockMvc.perform(put("/api/staff/" + sarahId).header(TOKEN_HEADER, morgan)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"1234\"}"))
                .andExpect(status().isConflict());

        // Sarah's PIN is what it was
        mockMvc.perform(signInWith("1234").with(from(PATIO_TERMINAL))).andExpect(status().isUnauthorized());
        assertEquals("Sarah", signIn("1111", PATIO_TERMINAL).get("staff").get("name").asText());
    }

    @Test
    void testTheFirstManager_MustChooseANewPinBeforeDoingAnythingElse() throws Exception {
        // WHAT: A brand-new database: the first manager signs in with 1234 and tries to use the app
        // WHY: The starter PIN is the same everywhere and is printed in this repository.
        //      Until it is changed, anyone who can reach the backend is a manager. So the
        //      backend, not just the screen, refuses everything else until a new PIN is set.

        // Given - no staff at all, which is when the first manager is created
        staffRepository.deleteAll();
        assertTrue(staffService.ensureFirstManager());

        // When - the first manager signs in with the starter PIN
        JsonNode answer = signIn("1234", BAR_TERMINAL);
        String token = answer.get("token").asText();

        // Then - the answer says a new PIN is needed
        assertTrue(answer.get("staff").get("mustChangePin").asBoolean());

        // And - nothing else is allowed yet, with a code the screen can react to
        mockMvc.perform(get("/api/menu-items").header(TOKEN_HEADER, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PIN_CHANGE_REQUIRED"));
        mockMvc.perform(get("/api/staff").header(TOKEN_HEADER, token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PIN_CHANGE_REQUIRED"));

        // And - asking "who am I" still works (a reloaded page needs it), and says the same
        mockMvc.perform(get("/api/staff/me").header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePin").value(true));

        // When - they choose a new PIN
        mockMvc.perform(put("/api/staff/me/pin").header(TOKEN_HEADER, token).with(from(BAR_TERMINAL))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPin\":\"1234\",\"newPin\":\"4821\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePin").value(false));

        // Then - the same session carries on, now allowed everything a manager is
        mockMvc.perform(get("/api/staff").header(TOKEN_HEADER, token))
                .andExpect(status().isOk());

        // And - 1234 no longer signs anyone in; the new PIN does, with nothing left to change
        mockMvc.perform(signInWith("1234").with(from(PATIO_TERMINAL))).andExpect(status().isUnauthorized());
        assertFalse(signIn("4821", PATIO_TERMINAL).get("staff").get("mustChangePin").asBoolean());
    }
}
