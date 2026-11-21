package com.notapos.integration;

import com.notapos.dto.StaffCreateRequest;
import com.notapos.enums.StaffRole;
import com.notapos.repository.StaffRepository;
import com.notapos.repository.StoredSessionRepository;
import com.notapos.service.StaffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for a manager releasing a paused terminal (step A6 of the
 * Phase 2 plan, the owner's decision D11).
 *
 * THE FLOOR SCENARIO: Friday night, someone fumbles their PIN at the bar
 * terminal until it is paused. Before this, nothing could end a pause early
 * and the fifth pause lasted an hour: a terminal dead for an hour on the
 * busiest night, which is worse than the guessing it guards against.
 *
 * WHAT A MANAGER CAN DO NOW: see which terminals are paused and for how much
 * longer (GET /api/staff/paused-devices), and release one
 * (POST /api/staff/paused-devices/release). Releasing ends the pause and
 * forgets that terminal's wrong PINs. It signs nobody in: the person at the
 * bar still has to type a right PIN.
 *
 * "A DEVICE" is the address a request comes from. MockMvc lets a test say what
 * that address is (see from()).
 *
 * That no pause is longer than five minutes is tested in SignInGuardTest,
 * where the clock can be moved by hand. That only a manager may use these two
 * endpoints is in RoleMatrixIntegrationTest, with every other endpoint.
 *
 * The first version of these tests was written before the feature and failed.
 *
 * @author CJ
 */
class PausedTerminalsIntegrationTest extends BaseIntegrationTest {

    // Two terminals, and the office computer the manager is at
    private static final String BAR_TERMINAL = "192.168.1.21";
    private static final String PATIO_TERMINAL = "192.168.1.22";
    private static final String OFFICE = "192.168.1.5";

    @Autowired private StaffService staffService;
    @Autowired private StaffRepository staffRepository;
    @Autowired private StoredSessionRepository storedSessionRepository;

    private RequestPostProcessor morgan;        // A manager who is already signed in

    @BeforeEach
    void setUp() {
        storedSessionRepository.deleteAll();
        staffRepository.deleteAll();
        staffService.createStaff(new StaffCreateRequest("Morgan", "manager", "2222"));
        staffService.createStaff(new StaffCreateRequest("Sarah", "server", "1111"));

        morgan = signIn.as(2L, "Morgan", StaffRole.MANAGER);
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

    private void wrongPins(int howMany, String address) throws Exception {
        for (int attempt = 0; attempt < howMany; attempt++) {
            mockMvc.perform(signInWith("9999").with(from(address)))
                    .andExpect(status().isUnauthorized());
        }
    }

    private static MockHttpServletRequestBuilder release(String address) {
        return post("/api/staff/paused-devices/release")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"device\":\"" + address + "\"}");
    }

    // ------------------------------------------------------------------
    // Seeing what is paused
    // ------------------------------------------------------------------

    @Test
    void testAPausedTerminal_ShouldBeListedForAManagerWithTheTimeLeft() throws Exception {
        // WHAT: Five wrong PINs at the bar, then the manager's list
        // WHY: A manager cannot release what they cannot see. The address is how the
        //      terminal is told apart from the others; the time left says whether it is
        //      worth doing anything at all.

        wrongPins(5, BAR_TERMINAL);

        mockMvc.perform(get("/api/staff/paused-devices").with(morgan).with(from(OFFICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].device").value(BAR_TERMINAL))
                .andExpect(jsonPath("$[0].secondsLeft", allOf(greaterThan(0), lessThanOrEqualTo(30))))   // The first pause is 30 seconds
                .andExpect(jsonPath("$[0].thisDevice").value(false));
    }

    @Test
    void testATerminalWithAFewWrongPins_ShouldNotBeListed() throws Exception {
        // WHAT: Four wrong PINs (one short of a pause), then the manager's list
        // WHY: The list is of terminals nobody can sign in at, not of every mistype

        wrongPins(4, BAR_TERMINAL);

        mockMvc.perform(get("/api/staff/paused-devices").with(morgan).with(from(OFFICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ------------------------------------------------------------------
    // Releasing one
    // ------------------------------------------------------------------

    @Test
    void testReleasingATerminal_ShouldLetPeopleSignInThereAtOnce() throws Exception {
        // WHAT: The bar is paused; the manager releases it from the office; Sarah signs in at the bar
        // WHY: The floor scenario at the top of this file

        // Given - the bar is paused, even for a right PIN
        wrongPins(5, BAR_TERMINAL);
        mockMvc.perform(signInWith("1111").with(from(BAR_TERMINAL)))
                .andExpect(status().isTooManyRequests());

        // When - the answer to a release is the list as it now stands: nothing paused
        mockMvc.perform(release(BAR_TERMINAL).with(morgan).with(from(OFFICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // Then
        mockMvc.perform(signInWith("1111").with(from(BAR_TERMINAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff.name").value("Sarah"));
    }

    @Test
    void testReleasingATerminal_ShouldForgetItsWrongPins() throws Exception {
        // WHAT: After a release, four more wrong PINs at the bar, then a right one
        // WHY: A release that ended the pause and kept the count would have the terminal
        //      paused again at the next mistype. The manager has vouched for it: it
        //      starts again with all five tries.

        wrongPins(5, BAR_TERMINAL);
        mockMvc.perform(release(BAR_TERMINAL).with(morgan).with(from(OFFICE)))
                .andExpect(status().isOk());

        wrongPins(4, BAR_TERMINAL);

        mockMvc.perform(signInWith("1111").with(from(BAR_TERMINAL)))
                .andExpect(status().isOk());
    }

    @Test
    void testReleasingATerminal_ShouldSignNobodyIn() throws Exception {
        // WHAT: Release the bar, then ask "who am I" from the bar with no token
        // WHY: A release opens the PIN pad again. It is not a way past it.

        wrongPins(5, BAR_TERMINAL);
        mockMvc.perform(release(BAR_TERMINAL).with(morgan).with(from(OFFICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").doesNotExist());

        mockMvc.perform(get("/api/staff/me").with(from(BAR_TERMINAL)))
                .andExpect(status().isUnauthorized());
        // And a wrong PIN there is still a wrong PIN
        mockMvc.perform(signInWith("9999").with(from(BAR_TERMINAL)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void testReleasingOneTerminal_ShouldLeaveAnotherPaused() throws Exception {
        // WHAT: The bar and the patio are both paused; only the bar is released
        // WHY: Each terminal is its own decision. Releasing one must not quietly release
        //      a terminal the manager has not looked at.

        wrongPins(5, BAR_TERMINAL);
        wrongPins(5, PATIO_TERMINAL);

        mockMvc.perform(release(BAR_TERMINAL).with(morgan).with(from(OFFICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].device").value(PATIO_TERMINAL));

        mockMvc.perform(signInWith("1111").with(from(PATIO_TERMINAL)))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void testAManagerAlreadySignedInAtThePausedTerminal_ShouldBeAbleToReleaseIt() throws Exception {
        // WHAT: The manager is signed in at the bar terminal itself when it gets paused
        // WHY: A pause stops new sign-ins, not people already signed in. With one
        //      terminal in the building, this is the only way to release it. The list
        //      marks the terminal the manager is standing at.

        wrongPins(5, BAR_TERMINAL);

        mockMvc.perform(get("/api/staff/paused-devices").with(morgan).with(from(BAR_TERMINAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].device").value(BAR_TERMINAL))
                .andExpect(jsonPath("$[0].thisDevice").value(true));

        mockMvc.perform(release(BAR_TERMINAL).with(morgan).with(from(BAR_TERMINAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testReleasingATerminalThatIsNotPaused_ShouldChangeNothingAndNotFail() throws Exception {
        // WHAT: Release a terminal whose pause has already run out (or was never paused)
        // WHY: The manager's list can be a few seconds old. Pressing RELEASE on a pause
        //      that ended by itself a moment ago is not an error.

        mockMvc.perform(release(BAR_TERMINAL).with(morgan).with(from(OFFICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void testAReleaseThatNamesNoTerminal_ShouldBeRefused() throws Exception {
        // WHAT: A release request with an empty address
        // WHY: "Release nothing" is a mistake in the request, and should be answered as one

        mockMvc.perform(release("").with(morgan).with(from(OFFICE)))
                .andExpect(status().isBadRequest());
    }
}
