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

        when(staffService.signIn(eq("1111"), any())).thenReturn(
                new SignInResponse(new StaffView(1L, "Sarah", "server", true, false), "a-long-random-token"));

        mockMvc.perform(post("/api/staff/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"1111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staff.name").value("Sarah"))
                .andExpect(jsonPath("$.staff.role").value("server"))
                .andExpect(jsonPath("$.token").value("a-long-random-token"))
                .andExpect(jsonPath("$.staff.mustChangePin").value(false))
                // Nothing about the PIN comes back
                .andExpect(jsonPath("$.staff.pin").doesNotExist())
                .andExpect(jsonPath("$.staff.pinHash").doesNotExist());
    }

    @Test
    void testSignIn_WithAWrongPin_ShouldReturn401() throws Exception {
        // WHAT: Test sign-in with a PIN nobody has
        // WHY: The PIN pad shows the message and clears the dots

        when(staffService.signIn(eq("9999"), any())).thenThrow(new NotSignedInException("PIN not recognized."));

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

        verify(staffService, never()).signIn(any(), any());
    }

    @Test
    void testSignIn_ShouldTellTheServiceWhichDeviceTheRequestCameFrom() throws Exception {
        // WHAT: Test that the address a sign-in comes from reaches StaffService
        // WHY: Wrong PINs are counted per device. If the controller passed nothing, or
        //      the same thing every time, the whole restaurant would be one device again.

        when(staffService.signIn(any(), any())).thenThrow(new NotSignedInException("PIN not recognized."));

        mockMvc.perform(post("/api/staff/sign-in")
                .with(request -> {
                    request.setRemoteAddr("192.168.1.21");          // The bar terminal
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"9999\"}"))
                .andExpect(status().isUnauthorized());

        verify(staffService).signIn("9999", "192.168.1.21");
    }

    @Test
    void testSignIn_WhenPausedAfterTooManyWrongPins_ShouldReturn429() throws Exception {
        // WHAT: Test sign-in while the wrong-PIN limit is in effect
        // WHY: The PIN pad tells the person to wait, in words. The Retry-After header
        //      says the same in seconds, which is the standard way to tell a program.

        when(staffService.signIn(eq("1111"), any()))
                .thenThrow(new TooManyAttemptsException("Too many wrong PINs on this device. Try again in 5 minutes.", 300));

        mockMvc.perform(post("/api/staff/sign-in")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"pin\":\"1111\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "300"))
                .andExpect(jsonPath("$.message").value("Too many wrong PINs on this device. Try again in 5 minutes."));
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
                .andExpect(jsonPath("$.role").value("manager"))
                .andExpect(jsonPath("$.mustChangePin").value(false));
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
    // Choosing a new PIN for yourself, and the starter PIN
    // ------------------------------------------------------------------

    @Test
    void testChangeOwnPin_ShouldPassWhoTheTokenAndTheDeviceAlong() throws Exception {
        // WHAT: Test PUT /api/staff/me/pin
        // WHY: The service needs all three: who is changing (from the session, never from
        //      the request body), which session to keep signed in, and which device to
        //      count a wrong current PIN against

        when(staffService.changeOwnPin(any(), any(), any(), any(), any()))
                .thenReturn(new StaffView(1L, "Sarah", "server", true, false));

        mockMvc.perform(put("/api/staff/me/pin").with(SignIn.withToken(serverToken))
                .with(request -> {
                    request.setRemoteAddr("192.168.1.21");
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPin\":\"1111\",\"newPin\":\"4821\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sarah"))
                .andExpect(jsonPath("$.mustChangePin").value(false));

        verify(staffService).changeOwnPin(
                argThat(who -> who.staffId().equals(1L) && who.role() == StaffRole.SERVER),
                eq(serverToken), eq("1111"), eq("4821"), eq("192.168.1.21"));
    }

    @Test
    void testChangeOwnPin_WithPinsOfTheWrongShape_ShouldReturn400WithEachProblem() throws Exception {
        // WHAT: Test a PIN change where a PIN is missing or is not four digits
        // WHY: Not worth checking against anyone - and not counted as a wrong guess

        mockMvc.perform(put("/api/staff/me/pin").with(SignIn.withToken(serverToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPin\":\"48\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.currentPin").value("Current PIN is required"))
                .andExpect(jsonPath("$.fieldErrors.newPin").value("New PIN must be exactly 4 digits"));

        verify(staffService, never()).changeOwnPin(any(), any(), any(), any(), any());
    }

    @Test
    void testChangeOwnPin_WithNoSignIn_ShouldReturn401() throws Exception {
        // WHAT: Test a PIN change by nobody
        // WHY: It changes the PIN of whoever is signed in. With nobody signed in there is no one to change.

        mockMvc.perform(put("/api/staff/me/pin")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPin\":\"1111\",\"newPin\":\"4821\"}"))
                .andExpect(status().isUnauthorized());

        verify(staffService, never()).changeOwnPin(any(), any(), any(), any(), any());
    }

    @Test
    void testSomeoneWhoMustChangeTheirPin_ShouldBeRefusedEverythingElse() throws Exception {
        // WHAT: Test a manager who signed in with the starter PIN asking for the staff list
        // WHY: Until the starter PIN is changed, anyone who knows it is a manager. So the
        //      backend, not only the screen, allows nothing else. The code tells the
        //      frontend which screen to show.

        mockMvc.perform(get("/api/staff").with(signIn.asManagerWhoMustChangePin()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PIN_CHANGE_REQUIRED"))
                .andExpect(jsonPath("$.message").value("Choose a new PIN before doing anything else."));

        mockMvc.perform(post("/api/staff").with(signIn.asManagerWhoMustChangePin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alex\",\"role\":\"manager\",\"pin\":\"4444\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PIN_CHANGE_REQUIRED"));

        verify(staffService, never()).getAllStaff();
        verify(staffService, never()).createStaff(any());
    }

    @Test
    void testSomeoneWhoMustChangeTheirPin_MayStillAskWhoTheyAreChooseAPinAndSignOut() throws Exception {
        // WHAT: Test the three things the starter-PIN holder is allowed
        // WHY: Choosing a PIN is the way out. "Who am I" is what a reloaded page asks to
        //      learn it must show the new-PIN screen. Signing out lets someone else sign in.

        when(staffService.changeOwnPin(any(), any(), any(), any(), any()))
                .thenReturn(new StaffView(999L, "Manager", "manager", true, false));

        mockMvc.perform(get("/api/staff/me").with(signIn.asManagerWhoMustChangePin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePin").value(true));

        mockMvc.perform(put("/api/staff/me/pin").with(signIn.asManagerWhoMustChangePin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPin\":\"1234\",\"newPin\":\"4821\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/staff/sign-out").with(signIn.asManagerWhoMustChangePin()))
                .andExpect(status().isNoContent());
    }

    // ------------------------------------------------------------------
    // Managing staff (managers only)
    // ------------------------------------------------------------------

    @Test
    void testGetAllStaff_AsAManager_ShouldReturnTheList() throws Exception {
        // WHAT: Test GET /api/staff signed in as a manager
        // WHY: The Staff screen lists everyone

        when(staffService.getAllStaff()).thenReturn(List.of(
                new StaffView(2L, "Morgan", "manager", true, false),
                new StaffView(1L, "Sarah", "server", true, false)));

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
                .thenReturn(new StaffView(3L, "Alex", "bartender", true, false));

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
                .thenReturn(new StaffView(1L, "Sarah", "bartender", true, false));

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
