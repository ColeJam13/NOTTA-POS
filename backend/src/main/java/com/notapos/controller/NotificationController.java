package com.notapos.controller;

import com.notapos.dto.MessageRequest;
import com.notapos.dto.NotificationView;
import com.notapos.security.Permissions;
import com.notapos.security.StaffSession;
import com.notapos.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API Controller for notifications: the bell in the top bar.
 *
 * Every endpoint here is about the SIGNED-IN PERSON'S OWN notifications. None
 * takes a staff ID: who is asking comes from the sign-in, so there is no way to
 * read or dismiss someone else's by naming them.
 *
 * Anyone signed in reads and dismisses their own. Writing a message to the
 * floor is for the kitchen, bartenders, and managers (the owner's decision D5).
 *
 * @author CJ
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Autowired
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @PreAuthorize(Permissions.OWN_NOTIFICATIONS)
    @GetMapping                                                         // My notifications that I have not dismissed, newest first
    public ResponseEntity<List<NotificationView>> getMine(@AuthenticationPrincipal StaffSession currentStaff) {
        return ResponseEntity.ok(notificationService.getMine(currentStaff));
    }

    @PreAuthorize(Permissions.OWN_NOTIFICATIONS)
    @PostMapping("/{id}/dismiss")                                       // Dismiss one of mine (404 if it is not mine)
    public ResponseEntity<Void> dismiss(@PathVariable Long id, @AuthenticationPrincipal StaffSession currentStaff) {
        notificationService.dismiss(id, currentStaff);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize(Permissions.OWN_NOTIFICATIONS)
    @PostMapping("/dismiss-all")                                        // Dismiss everything on my list
    public ResponseEntity<Void> dismissAll(@AuthenticationPrincipal StaffSession currentStaff) {
        notificationService.dismissAll(currentStaff);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize(Permissions.SEND_MESSAGES)                            // The kitchen, bartenders, and managers
    @PostMapping("/messages")                                           // Write a message to everyone else
    public ResponseEntity<NotificationView> sendMessage(@Valid @RequestBody MessageRequest request,
                                                        @AuthenticationPrincipal StaffSession currentStaff) {
        NotificationView sent = notificationService.sendMessage(request.text(), currentStaff);
        return ResponseEntity.status(HttpStatus.CREATED).body(sent);
    }
}
