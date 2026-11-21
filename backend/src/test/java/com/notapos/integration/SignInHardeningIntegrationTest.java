package com.notapos.integration;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.notapos.dto.StaffCreateRequest;
import com.notapos.dto.StaffUpdateRequest;
import com.notapos.repository.StaffRepository;
import com.notapos.repository.StoredSessionRepository;
import com.notapos.security.DatabaseStaffSessionStore;
import com.notapos.security.StaffSessionStore;
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
import java.time.Clock;
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
 *       The first four tests (one for each, and one for the PIN 1234 being
 *       refused as a new PIN) were written before the fix and failed.
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
    @Autowired private StoredSessionRepository storedSessionRepository;
    @Autowired private StaffSessionStore sessions;
    @Autowired private Clock clock;

    @BeforeEach
    void setUp() {
        storedSessionRepository.deleteAll();
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
        assertEquals("Sarah", signIn("1111", PATIO_TERMINAL).get("staff").get("name").asString());

        // And - the bar terminal is paused, even for a right PIN, and is told for how long
        mockMvc.perform(signInWith("1111").with(from(BAR_TERMINAL)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many wrong PINs on this device. Try again in 30 seconds, or ask a manager to release this terminal."));
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
        String token = signIn("2222", BAR_TERMINAL).get("token").asString();

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

    @Test
    void testASession_ShouldStillWorkAfterTheBackendForgetsEverythingInMemory() throws Exception {
        // WHAT: Sign in, then look the token up with a newly built session store
        // WHY: A restart throws away every object and builds them again; only the
        //      database is left. The old store kept its list in a field, so a restart
        //      signed the whole floor out mid-shift.

        // Given
        String token = signIn("1111", PATIO_TERMINAL).get("token").asString();

        // When - "the backend restarts": a store that has never seen this sign-in
        StaffSessionStore afterRestart = new DatabaseStaffSessionStore(storedSessionRepository, clock);

        // Then
        assertEquals("Sarah", afterRestart.sessionFor(token).orElseThrow().name());
    }

    @Test
    void testSignOut_ShouldRemoveTheRow() throws Exception {
        // WHAT: Sign in twice as the same person, sign one of them out
        // WHY: Signing out must end that session for good, in the database, and leave
        //      the same person's other device signed in

        // Given
        String bar = signIn("1111", BAR_TERMINAL).get("token").asString();
        String patio = signIn("1111", PATIO_TERMINAL).get("token").asString();
        assertEquals(2, storedSessionRepository.count());

        // When
        mockMvc.perform(post("/api/staff/sign-out").header(TOKEN_HEADER, bar))
                .andExpect(status().isNoContent());

        // Then
        assertEquals(1, storedSessionRepository.count());
        mockMvc.perform(get("/api/staff/me").header(TOKEN_HEADER, bar)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/staff/me").header(TOKEN_HEADER, patio)).andExpect(status().isOk());
    }

    @Test
    void testARequest_ShouldNotWriteToTheDatabaseJustToBeIdentified() throws Exception {
        // WHAT: Send several requests with a good token and compare the session row before and after
        // WHY: Every request looks its session up. The screens ask something every few
        //      seconds, so a write per request would be thousands of writes an hour.
        //      "Last used" is only written down when the note is more than a few minutes old.

        // Given
        String token = signIn("2222", BAR_TERMINAL).get("token").asString();
        Map<String, Object> before = jdbcTemplate.queryForMap("SELECT * FROM staff_sessions");

        // When
        for (int request = 0; request < 5; request++) {
            mockMvc.perform(get("/api/staff/me").header(TOKEN_HEADER, token)).andExpect(status().isOk());
        }

        // Then
        assertEquals(before, jdbcTemplate.queryForMap("SELECT * FROM staff_sessions"));
    }

    @Test
    void testCorrectingSomeonesName_ShouldKeepThemSignedInUnderTheNewName() throws Exception {
        // WHAT: A manager fixes the spelling of a name while that person is signed in
        // WHY: The session keeps a copy of the name, and a session can now last for days.
        //      Without updating the copy, the orders that person opens would carry the
        //      old spelling until they next signed out.

        // Given
        String sarah = signIn("1111", PATIO_TERMINAL).get("token").asString();
        Long sarahId = staffRepository.findAll().stream()
                .filter(staff -> staff.getName().equals("Sarah")).findFirst().orElseThrow().getStaffId();

        // When
        staffService.updateStaff(sarahId, new StaffUpdateRequest("Sarah Miller", null, null, null));

        // Then
        mockMvc.perform(get("/api/staff/me").header(TOKEN_HEADER, sarah))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sarah Miller"));
    }

    @Test
    void testAManagerChangingSomeonesPin_ShouldEndTheirSessionsInTheDatabase() throws Exception {
        // WHAT: Sarah is signed in on two devices; a manager gives her a new PIN
        // WHY: The change must take effect on every device at once. With sessions in the
        //      database this is a delete of her rows, in the same transaction as the PIN.

        // Given
        String bar = signIn("1111", BAR_TERMINAL).get("token").asString();
        String patio = signIn("1111", PATIO_TERMINAL).get("token").asString();
        String morgan = signIn("2222", BAR_TERMINAL).get("token").asString();
        Long sarahId = staffRepository.findAll().stream()
                .filter(staff -> staff.getName().equals("Sarah")).findFirst().orElseThrow().getStaffId();

        // When
        mockMvc.perform(put("/api/staff/" + sarahId).header(TOKEN_HEADER, morgan)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"4444\"}"))
                .andExpect(status().isOk());

        // Then - both of Sarah's sessions are gone; the manager's is not
        mockMvc.perform(get("/api/staff/me").header(TOKEN_HEADER, bar)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/staff/me").header(TOKEN_HEADER, patio)).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/staff/me").header(TOKEN_HEADER, morgan)).andExpect(status().isOk());
        assertEquals(1, storedSessionRepository.count());
    }

    // ------------------------------------------------------------------
    // Choosing a new PIN for yourself
    // ------------------------------------------------------------------

    @Test
    void testChangeOwnPin_ShouldSignThePersonOutEverywhereElseAndKeepThisDevice() throws Exception {
        // WHAT: Sarah, signed in at the bar and the patio, chooses a new PIN at the bar
        // WHY: The device she did it on just proved who she is and carries on. The other
        //      device was signed in with a PIN that no longer exists.

        // Given
        String bar = signIn("1111", BAR_TERMINAL).get("token").asString();
        String patio = signIn("1111", PATIO_TERMINAL).get("token").asString();

        // When
        mockMvc.perform(put("/api/staff/me/pin").header(TOKEN_HEADER, bar).with(from(BAR_TERMINAL))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPin\":\"1111\",\"newPin\":\"4821\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sarah"));

        // Then
        mockMvc.perform(get("/api/staff/me").header(TOKEN_HEADER, bar)).andExpect(status().isOk());
        mockMvc.perform(get("/api/staff/me").header(TOKEN_HEADER, patio)).andExpect(status().isUnauthorized());
        mockMvc.perform(signInWith("1111").with(from(PATIO_TERMINAL))).andExpect(status().isUnauthorized());
        assertEquals("Sarah", signIn("4821", PATIO_TERMINAL).get("staff").get("name").asString());
    }

    @Test
    void testChangeOwnPin_GuessingTheCurrentPin_ShouldPauseTheDeviceLikeThePinPadDoes() throws Exception {
        // WHAT: Someone at a terminal Sarah left signed in tries PINs on the change-PIN request
        // WHY: It checks a PIN, so it is a place to guess one. Without the same limit as
        //      sign-in, a signed-in terminal would give ten thousand free guesses at its owner's PIN.

        // Given
        String token = signIn("1111", BAR_TERMINAL).get("token").asString();

        // When - five wrong guesses at the current PIN
        for (int guess = 0; guess < 5; guess++) {
            mockMvc.perform(put("/api/staff/me/pin").header(TOKEN_HEADER, token).with(from(BAR_TERMINAL))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"currentPin\":\"900" + guess + "\",\"newPin\":\"4821\"}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("That is not your current PIN."));
        }

        // Then - the sixth is refused without being looked at, even though it is right
        mockMvc.perform(put("/api/staff/me/pin").header(TOKEN_HEADER, token).with(from(BAR_TERMINAL))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPin\":\"1111\",\"newPin\":\"4821\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "30"));

        // And - Sarah's PIN is unchanged, and she is still signed in (a pause stops sign-in, not work)
        mockMvc.perform(get("/api/staff/me").header(TOKEN_HEADER, token)).andExpect(status().isOk());
        assertEquals("Sarah", signIn("1111", PATIO_TERMINAL).get("staff").get("name").asString());
    }

    // ------------------------------------------------------------------
    // The starter PIN
    // ------------------------------------------------------------------

    @Test
    void testTheStarterPin_ShouldBeRefusedAsANewPin() throws Exception {
        // WHAT: Try to give someone the PIN 1234, when adding them and when changing them
        // WHY: 1234 is the PIN every new database starts with, so it is the first one
        //      anybody would guess. Nobody may hold it.

        String morgan = signIn("2222", BAR_TERMINAL).get("token").asString();
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
        assertEquals("Sarah", signIn("1111", PATIO_TERMINAL).get("staff").get("name").asString());
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
        String token = answer.get("token").asString();

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

    @Test
    void testAManagerStillOnTheStarterPin_ShouldBeFoundAtStartUpAndMadeToChangeIt() throws Exception {
        // WHAT: A database from before this change: its manager signs in with 1234 and
        //       nothing marks them. Then the backend starts again.
        // WHY: This is the owner's own database. The migration cannot tell who holds
        //      1234 (only hashes are stored); the start-up check can.

        // Given - the first manager as older versions created them: PIN 1234, not marked
        staffRepository.deleteAll();
        staffService.ensureFirstManager();
        jdbcTemplate.update("UPDATE staff SET must_change_pin = false");
        String token = signIn("1234", BAR_TERMINAL).get("token").asString();
        mockMvc.perform(get("/api/staff").header(TOKEN_HEADER, token)).andExpect(status().isOk());

        // When - what FirstManagerSetup does at every start
        assertEquals(1, staffService.requireChangeOfStarterPin());

        // Then - the old session is ended, and the next sign-in must choose a new PIN
        mockMvc.perform(get("/api/staff").header(TOKEN_HEADER, token)).andExpect(status().isUnauthorized());
        assertTrue(signIn("1234", BAR_TERMINAL).get("staff").get("mustChangePin").asBoolean());

        // And - the next start finds nothing more to do
        assertEquals(0, staffService.requireChangeOfStarterPin());
    }
}
