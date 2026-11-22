package com.notapos.controller;

import com.notapos.dto.ChallengeRequest;
import com.notapos.dto.ChallengeView;
import com.notapos.enums.StaffRole;
import com.notapos.exception.ChallengeRuleException;
import com.notapos.exception.InvalidChallengeException;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.security.StaffSession;
import com.notapos.service.ChallengeService;
import com.notapos.testsupport.ControllerTest;
import com.notapos.testsupport.SignIn;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import static org.hamcrest.Matchers.containsStringIgnoringCase;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for ChallengeController.
 *
 * Tests the REST endpoints for challenges: who may call each, what a request
 * must hold, and how a refusal from the service reaches the screen.
 * Uses MockMvc to simulate HTTP requests without starting full server.
 *
 * @author CJ
 */

@ControllerTest(ChallengeController.class)
class ChallengeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // The real sign-in check runs in controller tests. signIn makes a request come from a
    // signed-in person: .with(signIn.asManager()), .with(signIn.asServer()), ... (see SignIn)
    @Autowired
    private SignIn signIn;

    @MockitoBean
    private ChallengeService challengeService;

    // A request the annotations accept: reach $500 in sales on one evening
    private static final String BIG_NIGHT = "{\"name\":\"Big night\",\"description\":\"Reach 500 tonight\",\"measure\":\"total_sales\","
            + "\"target\":500,\"startsAt\":\"2026-11-18T17:00:00\",\"endsAt\":\"2026-11-18T23:00\",\"prize\":\"$20\"}";

    private ChallengeView bigNight() {
        return new ChallengeView(31L, "Big night", "Reach 500 tonight", "total_sales", new BigDecimal("500.00"),
                null, null, null, null,
                LocalDateTime.of(2026, 11, 18, 17, 0), LocalDateTime.of(2026, 11, 18, 23, 0), "$20", "Morgan", "active",
                List.of(
                        new ChallengeView.Participant(7L, "Sarah", new BigDecimal("520.50"), 9, true, true,
                                LocalDateTime.of(2026, 11, 18, 22, 15), "Morgan"),
                        new ChallengeView.Participant(8L, "Dan", new BigDecimal("140.00"), 3, false, false, null, null)));
    }

    // ------------------------------------------------------------------
    // Reading: everyone
    // ------------------------------------------------------------------

    @Test
    void testGetChallenges_ShouldReturnEachChallengeWithEveryonesProgress() throws Exception {
        // WHAT: Test GET /api/challenges
        // WHY: The Challenges screen reads everything it shows from this one response

        when(challengeService.getChallenges()).thenReturn(List.of(bigNight()));

        mockMvc.perform(get("/api/challenges").with(signIn.asServer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].challengeId").value(31))
                .andExpect(jsonPath("$[0].name").value("Big night"))
                .andExpect(jsonPath("$[0].measure").value("total_sales"))
                .andExpect(jsonPath("$[0].target").value(500.00))
                .andExpect(jsonPath("$[0].startsAt").value("2026-11-18T17:00:00"))     // The restaurant's clock, no zone
                .andExpect(jsonPath("$[0].endsAt").value("2026-11-18T23:00:00"))
                .andExpect(jsonPath("$[0].prize").value("$20"))
                .andExpect(jsonPath("$[0].state").value("active"))
                .andExpect(jsonPath("$[0].participants.length()").value(2))
                .andExpect(jsonPath("$[0].participants[0].staffId").value(7))
                .andExpect(jsonPath("$[0].participants[0].progress").value(520.50))
                .andExpect(jsonPath("$[0].participants[0].completed").value(true))
                .andExpect(jsonPath("$[0].participants[0].awardedAt").value("2026-11-18T22:15:00"))
                .andExpect(jsonPath("$[0].participants[0].awardedByName").value("Morgan"))
                .andExpect(jsonPath("$[0].participants[1].completed").value(false))
                .andExpect(jsonPath("$[0].participants[1].awardedAt").doesNotExist());
    }

    @Test
    void testGetChallenges_AsTheKitchenOrAHost_ShouldBeAllowed_AndCarryNoTip() throws Exception {
        // WHAT: Test the list as the two roles allowed the least
        // WHY: Everyone signed in reads the challenges with everyone's progress (D6).
        //      Progress is sales and item counts; a tip is never part of it.

        when(challengeService.getChallenges()).thenReturn(List.of(bigNight()));

        mockMvc.perform(get("/api/challenges").with(signIn.asKitchen()))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsStringIgnoringCase("tip"))));
        mockMvc.perform(get("/api/challenges").with(signIn.asHost())).andExpect(status().isOk());
    }

    @Test
    void testGetChallenges_WithNobodySignedIn_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/challenges")).andExpect(status().isUnauthorized());

        verify(challengeService, never()).getChallenges();
    }

    // ------------------------------------------------------------------
    // Making one: managers
    // ------------------------------------------------------------------

    @Test
    void testCreateChallenge_AsAManager_ShouldReturn201_AndHandOverTheRequestAndWhoIsAsking() throws Exception {
        // WHAT: Test POST /api/challenges
        // WHY: Who made the challenge is the signed-in manager, taken from the sign-in.
        //      A time with no seconds ("23:00"), as a date-and-time box writes it, is read.

        when(challengeService.create(any(), any())).thenReturn(bigNight());

        mockMvc.perform(post("/api/challenges").with(signIn.as(2L, "Morgan", StaffRole.MANAGER))
                        .contentType(MediaType.APPLICATION_JSON).content(BIG_NIGHT))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.challengeId").value(31));

        ArgumentCaptor<ChallengeRequest> request = ArgumentCaptor.forClass(ChallengeRequest.class);
        ArgumentCaptor<StaffSession> who = ArgumentCaptor.forClass(StaffSession.class);
        verify(challengeService).create(request.capture(), who.capture());
        assertEquals("Big night", request.getValue().name());
        assertEquals("total_sales", request.getValue().measure());
        assertEquals(0, new BigDecimal("500").compareTo(request.getValue().target()));
        assertEquals(LocalDateTime.of(2026, 11, 18, 17, 0), request.getValue().startsAt());
        assertEquals(LocalDateTime.of(2026, 11, 18, 23, 0), request.getValue().endsAt());
        assertNull(request.getValue().menuItemId());
        assertEquals(2L, who.getValue().staffId());
        assertEquals("Morgan", who.getValue().name());
    }

    @Test
    void testEveryChange_AsAnyoneButAManager_ShouldReturn403() throws Exception {
        // WHAT: Test making, changing, ending, deleting, and marking an award as a server and as a bartender
        // WHY: Only managers run challenges. A bartender may 86 an item and write a
        //      message, which is why this has a rule of its own and does not borrow theirs.

        for (var asWhom : List.of(signIn.asServer(), signIn.asBartender(), signIn.asKitchen(), signIn.asHost())) {
            mockMvc.perform(post("/api/challenges").with(asWhom).contentType(MediaType.APPLICATION_JSON).content(BIG_NIGHT))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put("/api/challenges/31").with(asWhom).contentType(MediaType.APPLICATION_JSON).content(BIG_NIGHT))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post("/api/challenges/31/end").with(asWhom)).andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/challenges/31").with(asWhom)).andExpect(status().isForbidden());
            mockMvc.perform(post("/api/challenges/31/awards/7").with(asWhom)).andExpect(status().isForbidden());
        }

        verifyNoInteractions(challengeService);
    }

    @Test
    void testCreateChallenge_WithFieldsMissingOrOutOfRange_ShouldReturn400NamingEachField() throws Exception {
        // WHAT: Test a request with no name, a target of zero, no measure, and no dates
        // WHY: The form shows each message beside its box

        mockMvc.perform(post("/api/challenges").with(signIn.asManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  \",\"target\":0,\"minChecks\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("A challenge needs a name"))
                .andExpect(jsonPath("$.fieldErrors.target").value("The target has to be more than zero"))
                .andExpect(jsonPath("$.fieldErrors.measure").value("Pick what the challenge measures"))
                .andExpect(jsonPath("$.fieldErrors.minChecks").value("The number of checks has to be at least 1"))
                .andExpect(jsonPath("$.fieldErrors.startsAt").value("A challenge needs a start"))
                .andExpect(jsonPath("$.fieldErrors.endsAt").value("A challenge needs an end"));

        verify(challengeService, never()).create(any(), any());
    }

    @Test
    void testCreateChallenge_WithTextTooLong_ShouldReturn400() throws Exception {
        // WHAT: Test a name longer than the column that holds it
        // WHY: Refused with words, not cut off silently or turned into a database error

        String longName = "x".repeat(61);
        mockMvc.perform(post("/api/challenges").with(signIn.asManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BIG_NIGHT.replace("Big night", longName)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").value("The name can be 60 characters at most"));
    }

    @Test
    void testCreateChallenge_WhenTheFieldsDoNotAddUp_ShouldReturn400WithTheServicesWords() throws Exception {
        // WHAT: Test a refusal from the service's own checks (rules across fields)
        // WHY: The form shows the message as-is, so it must reach the screen unchanged

        when(challengeService.create(any(), any()))
                .thenThrow(new InvalidChallengeException("Pick the menu category or the item this challenge counts."));

        mockMvc.perform(post("/api/challenges").with(signIn.asManager())
                        .contentType(MediaType.APPLICATION_JSON).content(BIG_NIGHT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Pick the menu category or the item this challenge counts."));
    }

    // ------------------------------------------------------------------
    // Changing, ending, deleting, awarding: managers
    // ------------------------------------------------------------------

    @Test
    void testChangeChallenge_ShouldReturnTheChallengeAsItNowIs() throws Exception {
        when(challengeService.change(eq(31L), any(), any())).thenReturn(bigNight());

        mockMvc.perform(put("/api/challenges/31").with(signIn.asManager())
                        .contentType(MediaType.APPLICATION_JSON).content(BIG_NIGHT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Big night"));
    }

    @Test
    void testChangeChallenge_ThatHasAnAward_ShouldReturn409WithTheReason() throws Exception {
        // WHAT: Test a change the service refuses because of the challenge's state
        // WHY: 409 "the challenge is not in a state that allows this", with words for the manager

        when(challengeService.change(eq(31L), any(), any()))
                .thenThrow(new ChallengeRuleException("A prize has been marked awarded on this challenge, so it is a record now and cannot be changed."));

        mockMvc.perform(put("/api/challenges/31").with(signIn.asManager())
                        .contentType(MediaType.APPLICATION_JSON).content(BIG_NIGHT))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A prize has been marked awarded on this challenge, so it is a record now and cannot be changed."));
    }

    @Test
    void testEndChallenge_ShouldReturnTheChallengeNowFinished() throws Exception {
        when(challengeService.endNow(eq(31L), any())).thenReturn(bigNight());

        mockMvc.perform(post("/api/challenges/31/end").with(signIn.asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.challengeId").value(31));
    }

    @Test
    void testDeleteChallenge_ShouldReturn204() throws Exception {
        mockMvc.perform(delete("/api/challenges/31").with(signIn.asManager()))
                .andExpect(status().isNoContent());

        verify(challengeService).delete(eq(31L), any());
    }

    @Test
    void testDeleteChallenge_ThatHasAnAward_ShouldReturn409() throws Exception {
        doThrow(new ChallengeRuleException("A prize has been marked awarded on this challenge. It stays as a record and cannot be deleted."))
                .when(challengeService).delete(eq(31L), any());

        mockMvc.perform(delete("/api/challenges/31").with(signIn.asManager()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("A prize has been marked awarded on this challenge. It stays as a record and cannot be deleted."));
    }

    @Test
    void testMarkAwarded_ShouldNameThePersonInTheAddress_AndTheManagerFromTheSignIn() throws Exception {
        // WHAT: Test POST /api/challenges/{id}/awards/{staffId}
        // WHY: The award records which manager marked it; that is whoever is signed in

        when(challengeService.markAwarded(anyLong(), anyLong(), any())).thenReturn(bigNight());

        mockMvc.perform(post("/api/challenges/31/awards/7").with(signIn.as(2L, "Morgan", StaffRole.MANAGER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.participants[0].awardedByName").value("Morgan"));

        ArgumentCaptor<StaffSession> who = ArgumentCaptor.forClass(StaffSession.class);
        verify(challengeService).markAwarded(eq(31L), eq(7L), who.capture());
        assertEquals(2L, who.getValue().staffId());
    }

    @Test
    void testMarkAwarded_ForSomeoneWhoHasNotCompletedIt_ShouldReturn409() throws Exception {
        when(challengeService.markAwarded(anyLong(), anyLong(), any()))
                .thenThrow(new ChallengeRuleException("Dan has not completed this challenge."));

        mockMvc.perform(post("/api/challenges/31/awards/8").with(signIn.asManager()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Dan has not completed this challenge."));
    }

    @Test
    void testAChallengeThatDoesNotExist_ShouldReturn404() throws Exception {
        when(challengeService.endNow(eq(404L), any())).thenThrow(new ResourceNotFoundException("Challenge not found with id: 404"));

        mockMvc.perform(post("/api/challenges/404/end").with(signIn.asManager()))
                .andExpect(status().isNotFound());
    }
}
