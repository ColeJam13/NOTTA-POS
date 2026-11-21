package com.notapos.controller;

import com.notapos.dto.ChangePinRequest;
import com.notapos.dto.SignInRequest;
import com.notapos.dto.SignInResponse;
import com.notapos.dto.StaffCreateRequest;
import com.notapos.dto.StaffUpdateRequest;
import com.notapos.dto.StaffView;
import com.notapos.security.StaffTokenFilter;
import com.notapos.security.StaffSession;
import com.notapos.service.StaffService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.notapos.security.Permissions;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST API Controller for staff: signing in with a PIN, and managing who works here.
 *
 * Signing in is the one endpoint in the whole backend open to a request with
 * no sign-in (you cannot be asked to sign in before signing in; SecurityConfig
 * names it). Signing out, "who am I", and choosing a new PIN for yourself are
 * for anyone who is signed in. Listing and changing staff is for managers only
 * (@PreAuthorize).
 *
 * "THE DEVICE": sign-in and the PIN change hand StaffService the address the
 * request came from (request.getRemoteAddr()). Wrong PINs are counted per
 * device, so one terminal's mistakes do not pause another's (see SignInGuard).
 *
 * @author CJ
 */

@RestController
@RequestMapping("/api/staff")
public class StaffController {

    private final StaffService staffService;

    @Autowired
    public StaffController(StaffService staffService) {
        this.staffService = staffService;
    }

    @PostMapping("/sign-in")                                                // Sign in with a PIN (401 if no active staff member has it, 429 while paused after wrong PINs)
    public ResponseEntity<SignInResponse> signIn(@Valid @RequestBody SignInRequest request,
                                                 HttpServletRequest httpRequest) {
        return ResponseEntity.ok(staffService.signIn(request.pin(), httpRequest.getRemoteAddr()));
    }

    @PostMapping("/sign-out")                                               // Sign out: end the session this token belongs to
    public ResponseEntity<Void> signOut(
            @RequestHeader(name = StaffTokenFilter.TOKEN_HEADER, required = false) String token) {
        // Only a signed-in person reaches this. A token that is already ended, or none at
        // all, is answered 401 before the controller: there is nothing left to sign out of,
        // and the frontend forgets the token whatever the answer is.
        staffService.signOut(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")                                                      // Who is signed in with this token (401 if nobody) - used when the page is reloaded
    public ResponseEntity<StaffView> getCurrentStaff(
            @AuthenticationPrincipal StaffSession currentStaff) {            // The signed-in person (never null: nobody else gets this far)
        return ResponseEntity.ok(new StaffView(
                currentStaff.staffId(), currentStaff.name(), currentStaff.role().getValue(), true,
                currentStaff.mustChangePin()));
    }

    // Choose a new PIN for yourself. The one thing a person holding the starter PIN may do
    // (SecurityConfig lets it through); open to every role, and said so with a rule so that
    // "changes data, no rule" still means "someone forgot".
    @PreAuthorize(Permissions.ANYONE_SIGNED_IN)
    @PutMapping("/me/pin")
    public ResponseEntity<StaffView> changeOwnPin(
            @Valid @RequestBody ChangePinRequest request,
            @AuthenticationPrincipal StaffSession currentStaff,
            @RequestHeader(name = StaffTokenFilter.TOKEN_HEADER) String token,     // This device's session carries on; the person's others are ended
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(staffService.changeOwnPin(
                currentStaff, token, request.currentPin(), request.newPin(), httpRequest.getRemoteAddr()));
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)
    @GetMapping                                                             // Get all staff, active or not (managers only)
    public ResponseEntity<List<StaffView>> getAllStaff() {
        return ResponseEntity.ok(staffService.getAllStaff());
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)
    @PostMapping                                                            // Add a staff member (managers only)
    public ResponseEntity<StaffView> createStaff(@Valid @RequestBody StaffCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(staffService.createStaff(request));
    }

    @PreAuthorize(Permissions.MANAGER_ONLY)
    @PutMapping("/{id}")                                                    // Change a staff member's name, role, PIN, or active flag (managers only)
    public ResponseEntity<StaffView> updateStaff(
            @PathVariable Long id,
            @Valid @RequestBody StaffUpdateRequest request) {
        return ResponseEntity.ok(staffService.updateStaff(id, request));
    }
}
