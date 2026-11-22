package com.notapos.controller;

import com.notapos.dto.NotificationView;
import com.notapos.enums.StaffRole;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.security.StaffSession;
import com.notapos.service.NotificationService;
import com.notapos.testsupport.ControllerTest;
import com.notapos.testsupport.SignIn;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller tests for NotificationController.
 *
 * Tests the four endpoints behind the bell in the top bar.
 * Uses MockMvc to simulate HTTP requests without starting full server.
 *
 * @author CJ
 */
@ControllerTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignIn signIn;              // Says who a request comes from: .with(signIn.asServer())

    @MockitoBean
    private NotificationService notificationService;

    private static final LocalDateTime NOON = LocalDateTime.of(2026, 11, 20, 12, 0, 0);

    // ------------------------------------------------------------------
    // Reading
    // ------------------------------------------------------------------

    @Test
    void testGetMine_ShouldReturnTheSignedInPersonsNotifications() throws Exception {
        // WHAT: Test GET /api/notifications
        // WHY: The bell's count and its panel are drawn from this one answer
        when(notificationService.getMine(any())).thenReturn(List.of(
                new NotificationView(12L, "item_86", "Garden Toast is 86'd", "Kit", NOON),
                new NotificationView(11L, "order_up", "Table W5 is up", "Kit", NOON.minusSeconds(60))));

        mockMvc.perform(get("/api/notifications").with(signIn.as(7L, "Sarah", StaffRole.SERVER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].notificationId").value(12))
                .andExpect(jsonPath("$[0].kind").value("item_86"))
                .andExpect(jsonPath("$[0].text").value("Garden Toast is 86'd"))
                .andExpect(jsonPath("$[0].fromName").value("Kit"))
                .andExpect(jsonPath("$[0].createdAt").value("2026-11-20T12:00:00"))        // The restaurant's clock, no zone: like every other time in JSON
                .andExpect(jsonPath("$[1].kind").value("order_up"));

        // The service was asked about the person who is signed in, and nobody the request named
        ArgumentCaptor<StaffSession> who = ArgumentCaptor.forClass(StaffSession.class);
        verify(notificationService).getMine(who.capture());
        assertEquals(7L, who.getValue().staffId());
    }

    @Test
    void testGetMine_WithNothing_ShouldReturnAnEmptyList() throws Exception {
        // WHY: "No notifications" is an empty list, not an error and not a missing answer
        when(notificationService.getMine(any())).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications").with(signIn.asKitchen()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void testGetMine_WithNobodySignedIn_ShouldReturn401() throws Exception {
        mockMvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(notificationService);
    }

    // ------------------------------------------------------------------
    // Dismissing
    // ------------------------------------------------------------------

    @Test
    void testDismiss_ShouldDismissItForTheSignedInPerson() throws Exception {
        mockMvc.perform(post("/api/notifications/12/dismiss").with(signIn.as(7L, "Sarah", StaffRole.SERVER)))
                .andExpect(status().isNoContent());

        ArgumentCaptor<StaffSession> who = ArgumentCaptor.forClass(StaffSession.class);
        verify(notificationService).dismiss(eq(12L), who.capture());
        assertEquals(7L, who.getValue().staffId());
    }

    @Test
    void testDismiss_WhenItIsNotTheirs_ShouldReturn404() throws Exception {
        // WHY: Somebody else's notification and one that does not exist answer the same way
        doThrow(new ResourceNotFoundException("Notification not found with id: 99"))
                .when(notificationService).dismiss(eq(99L), any());

        mockMvc.perform(post("/api/notifications/99/dismiss").with(signIn.asServer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void testDismissAll_ShouldDismissTheSignedInPersonsList() throws Exception {
        mockMvc.perform(post("/api/notifications/dismiss-all").with(signIn.as(7L, "Sarah", StaffRole.SERVER)))
                .andExpect(status().isNoContent());

        ArgumentCaptor<StaffSession> who = ArgumentCaptor.forClass(StaffSession.class);
        verify(notificationService).dismissAll(who.capture());
        assertEquals(7L, who.getValue().staffId());
    }

    // ------------------------------------------------------------------
    // Writing a message
    // ------------------------------------------------------------------

    private static final String FRYER = "{\"text\":\"Fryer is down for 10 minutes\"}";

    @Test
    void testSendMessage_FromTheKitchen_ShouldSendItAsThatPerson() throws Exception {
        when(notificationService.sendMessage(eq("Fryer is down for 10 minutes"), any()))
                .thenReturn(new NotificationView(44L, "message", "Fryer is down for 10 minutes", "Kit", NOON));

        mockMvc.perform(post("/api/notifications/messages").with(signIn.as(9L, "Kit", StaffRole.KITCHEN))
                        .contentType(MediaType.APPLICATION_JSON).content(FRYER))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.notificationId").value(44))
                .andExpect(jsonPath("$.fromName").value("Kit"));

        ArgumentCaptor<StaffSession> who = ArgumentCaptor.forClass(StaffSession.class);
        verify(notificationService).sendMessage(eq("Fryer is down for 10 minutes"), who.capture());
        assertEquals(9L, who.getValue().staffId());
    }

    @Test
    void testSendMessage_FromABartenderOrAManager_ShouldBeAllowed() throws Exception {
        when(notificationService.sendMessage(any(), any()))
                .thenReturn(new NotificationView(44L, "message", "x", "x", NOON));

        mockMvc.perform(post("/api/notifications/messages").with(signIn.as(8L, "Dan", StaffRole.BARTENDER))
                        .contentType(MediaType.APPLICATION_JSON).content(FRYER)).andExpect(status().isCreated());
        mockMvc.perform(post("/api/notifications/messages").with(signIn.asManager())
                        .contentType(MediaType.APPLICATION_JSON).content(FRYER)).andExpect(status().isCreated());
    }

    @Test
    void testSendMessage_FromAServerOrAHost_ShouldReturn403() throws Exception {
        // WHY: The owner's decision D5: the kitchen, bartenders, and managers write messages
        mockMvc.perform(post("/api/notifications/messages").with(signIn.asServer())
                        .contentType(MediaType.APPLICATION_JSON).content(FRYER)).andExpect(status().isForbidden());
        mockMvc.perform(post("/api/notifications/messages").with(signIn.as(4L, "Harper", StaffRole.HOST))
                        .contentType(MediaType.APPLICATION_JSON).content(FRYER)).andExpect(status().isForbidden());

        verify(notificationService, never()).sendMessage(any(), any());
    }

    @Test
    void testSendMessage_WithNothingWritten_ShouldReturn400() throws Exception {
        for (String body : new String[] { "{\"text\":\"\"}", "{\"text\":\"    \"}", "{}" }) {
            mockMvc.perform(post("/api/notifications/messages").with(signIn.asKitchen())
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest());
        }
        verify(notificationService, never()).sendMessage(any(), any());
    }

    @Test
    void testSendMessage_LongerThan200Characters_ShouldReturn400_AndExactly200_ShouldBeSent() throws Exception {
        when(notificationService.sendMessage(any(), any()))
                .thenReturn(new NotificationView(44L, "message", "x", "Kit", NOON));

        mockMvc.perform(post("/api/notifications/messages").with(signIn.asKitchen())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + "x".repeat(201) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(notificationService, never()).sendMessage(any(), any());

        mockMvc.perform(post("/api/notifications/messages").with(signIn.asKitchen())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"" + "x".repeat(200) + "\"}"))
                .andExpect(status().isCreated());
    }
}
