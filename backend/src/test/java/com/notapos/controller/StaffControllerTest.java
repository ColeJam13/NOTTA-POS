package com.notapos.controller;

import com.notapos.dto.SignInResponse;
import com.notapos.dto.StaffCreateRequest;
import com.notapos.dto.StaffUpdateRequest;
import com.notapos.dto.StaffView;
import com.notapos.enums.StaffRole;
import com.notapos.exception.NotSignedInException;
import com.notapos.exception.StaffRuleException;
import com.notapos.exception.TooManyAttemptsException;
import com.notapos.service.StaffService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.notapos.testsupport.ControllerTest;
import com.notapos.testsupport.SignIn;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for StaffController.
 *
 * Tests PIN sign-in and the manager-only staff endpoints.
 * Uses MockMvc to simulate HTTP requests without starting full server.
 *
 * The permission check is the real one here, so these tests also prove who
 * gets a 401, who gets a 403, and who gets in. This test is about tokens, so
 * it keeps two of its own (a manager's and a server's) instead of using
 * signIn.asManager().
 *
 * @author CJ
 */

@ControllerTest(StaffController.class)
class StaffControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Makes a request come from a signed-in person, and hands out real tokens (see SignIn)
    @Autowired
    private SignIn signIn;

    @MockBean
    private StaffService staffService;

    private String managerToken;
    private String serverToken;

    @BeforeEach
    void signInTestStaff() {
        managerToken = signIn.tokenFor(2L, "Morgan", StaffRole.MANAGER);
        serverToken = signIn.tokenFor(1L, "Sarah", StaffRole.SERVER);
    }

    // ------------------------------------------------------------------
    // Signing in and out
    // ------------------------------------------------------------------

    @Test
    void testSignIn_WithTheRightPin_ShouldReturnThePersonAndAToken() throws Exception {
        // WHAT: Test POST /api/staff/sign-in
        // WHY: The PIN pad sends the four digits and gets back who that is

        when(staffService.signIn("1111")).thenReturn(
                new SignInResponse(new StaffView(1L, "Sarah", "server", true), "a-long-random-token"));

        mockMvc.perform(post("/api/staff/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"1111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff.name").value("Sarah"))
                .andExpect(jsonPath("$.staff.role").value("server"))
                .andExpect(jsonPath("$.token").value("a-long-random-token"))
                // Nothing about the PIN comes back
                .andExpect(jsonPath("$.staff.pin").doesNotExist())
                .andExpect(jsonPath("$.staff.pinHash").doesNotExist());
    }

    @Test
    void testSignIn_WithAWrongPin_ShouldReturn401() throws Exception {
        // WHAT: Test sign-in with a PIN nobody has
        // WHY: The PIN pad shows the message and clears the dots

        when(staffService.signIn("9999")).thenThrow(new NotSignedInException("PIN not recognized."));

        mockMvc.perform(post("/api/staff/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"9999\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("PIN not recognized."));
    }

    @Test
    void testSignIn_WithSomethingThatIsNotFourDigits_ShouldReturn400() throws Exception {
        // WHAT: Test sign-in with a PIN of the wrong shape
        // WHY: Not worth checking against anyone - and not counted as a wrong guess

        for (String bad : List.of("{\"pin\":\"12\"}", "{\"pin\":\"12345\"}", "{\"pin\":\"abcd\"}", "{}")) {
            mockMvc.perform(post("/api/staff/sign-in")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(bad))
                    .andExpect(status().isBadRequest());
        }

        verify(staffService, never()).signIn(any());
    }

    @Test
    void testSignIn_WhenPausedAfterTooManyWrongPins_ShouldReturn429() throws Exception {
        // WHAT: Test sign-in while the wrong-PIN limit is in effect
        // WHY: The PIN pad tells the person to wait

        when(staffService.signIn("1111"))
                .thenThrow(new TooManyAttemptsException("Too many wrong PINs. Wait 30 seconds and try again."));

        mockMvc.perform(post("/api/staff/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"1111\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many wrong PINs. Wait 30 seconds and try again."));
    }

    @Test
    void testSignOut_ShouldPassTheTokenAlongAndReturn204() throws Exception {
        // WHAT: Test POST /api/staff/sign-out
        // WHY: Ends the session so the token stops working

        mockMvc.perform(post("/api/staff/sign-out").with(SignIn.withToken(serverToken)))
                .andExpect(status().isNoContent());

        verify(staffService).signOut(serverToken);
    }

    @Test
    void testGetCurrentStaff_ShouldSayWhoATokenBelongsTo() throws Exception {
        // WHAT: Test GET /api/staff/me
        // WHY: After a page reload the screen asks "am I still signed in, and as whom?"

        mockMvc.perform(get("/api/staff/me").with(SignIn.withToken(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffId").value(2))
                .andExpect(jsonPath("$.name").value("Morgan"))
                .andExpect(jsonPath("$.role").value("manager"));
    }

    @Test
    void testGetCurrentStaff_WithNoTokenOrAnOldOne_ShouldReturn401() throws Exception {
        // WHAT: Test GET /api/staff/me when nobody is signed in
        // WHY: The screen answers a 401 by showing the PIN pad

        mockMvc.perform(get("/api/staff/me"))
                .andExpect(status().isUnauthorized());

        // A token whose session has ended is the same as no token
        signIn.endSession(serverToken);
        mockMvc.perform(get("/api/staff/me").with(SignIn.withToken(serverToken)))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // Managing staff (managers only)
    // ------------------------------------------------------------------

    @Test
    void testGetAllStaff_AsAManager_ShouldReturnTheList() throws Exception {
        // WHAT: Test GET /api/staff signed in as a manager
        // WHY: The Staff screen lists everyone

        when(staffService.getAllStaff()).thenReturn(List.of(
                new StaffView(2L, "Morgan", "manager", true),
                new StaffView(1L, "Sarah", "server", true)));

        mockMvc.perform(get("/api/staff").with(SignIn.withToken(managerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Morgan"));
    }

    @Test
    void testGetAllStaff_WhenNotAManager_ShouldBeRefused() throws Exception {
        // WHAT: Test GET /api/staff with no sign-in, and signed in as a server
        // WHY: 401 = "we do not know who you are"; 403 = "we know, and no"

        mockMvc.perform(get("/api/staff"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Sign in to do that."));

        mockMvc.perform(get("/api/staff").with(SignIn.withToken(serverToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Your role (server) is not allowed to do that."));

        verify(staffService, never()).getAllStaff();
    }

    @Test
    void testCreateStaff_AsAManager_ShouldReturnCreated() throws Exception {
        // WHAT: Test POST /api/staff
        // WHY: A manager adds a new hire

        when(staffService.createStaff(new StaffCreateRequest("Alex", "bartender", "3333")))
                .thenReturn(new StaffView(3L, "Alex", "bartender", true));

        mockMvc.perform(post("/api/staff").with(SignIn.withToken(managerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alex\",\"role\":\"bartender\",\"pin\":\"3333\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.staffId").value(3))
                .andExpect(jsonPath("$.name").value("Alex"));
    }

    @Test
    void testCreateStaff_WithMissingFields_ShouldReturn400WithEachProblem() throws Exception {
        // WHAT: Test POST /api/staff with no name and a 3-digit PIN
        // WHY: The Staff screen shows the manager what to fix

        mockMvc.perform(post("/api/staff").with(SignIn.withToken(managerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"role\":\"server\",\"pin\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("Name is required"))
                .andExpect(jsonPath("$.fieldErrors.pin").value("PIN must be exactly 4 digits"));

        verify(staffService, never()).createStaff(any());
    }

    @Test
    void testCreateStaff_AsAServer_ShouldReturn403() throws Exception {
        // WHAT: Test POST /api/staff signed in as a server
        // WHY: Otherwise anyone could make themselves a manager

        mockMvc.perform(post("/api/staff").with(SignIn.withToken(serverToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Me\",\"role\":\"manager\",\"pin\":\"0000\"}"))
                .andExpect(status().isForbidden());

        verify(staffService, never()).createStaff(any());
    }

    @Test
    void testUpdateStaff_AsAManager_ShouldReturnTheUpdatedPerson() throws Exception {
        // WHAT: Test PUT /api/staff/{id} with only a new role
        // WHY: Fields that are left out are not changed

        when(staffService.updateStaff(eq(1L), eq(new StaffUpdateRequest(null, "bartender", null, null))))
                .thenReturn(new StaffView(1L, "Sarah", "bartender", true));

        mockMvc.perform(put("/api/staff/1").with(SignIn.withToken(managerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"role\":\"bartender\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("bartender"));
    }

    @Test
    void testUpdateStaff_WhenARuleIsBroken_ShouldReturn409WithTheReason() throws Exception {
        // WHAT: Test PUT /api/staff/{id} when the change breaks a staff rule
        // WHY: The Staff screen shows this message to the manager as-is

        when(staffService.updateStaff(eq(2L), any()))
                .thenThrow(new StaffRuleException("Morgan is the only manager. Make someone else a manager first."));

        mockMvc.perform(put("/api/staff/2").with(SignIn.withToken(managerToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"isActive\":false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Morgan is the only manager. Make someone else a manager first."));
    }

    @Test
    void testUpdateStaff_WithNoSignIn_ShouldReturn401() throws Exception {
        // WHAT: Test PUT /api/staff/{id} with no sign-in
        // WHY: Resetting someone's PIN would be a way to sign in as them

        mockMvc.perform(put("/api/staff/2")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"0000\"}"))
                .andExpect(status().isUnauthorized());

        verify(staffService, never()).updateStaff(any(), any());
    }
}
