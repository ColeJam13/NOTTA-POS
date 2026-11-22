package com.notapos.controller;

import com.notapos.dto.ChallengeRequest;
import com.notapos.dto.ChallengeView;
import com.notapos.security.Permissions;
import com.notapos.security.StaffSession;
import com.notapos.service.ChallengeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API Controller for challenges.
 *
 * Everyone signed in reads them, with everyone's progress. Managers make,
 * change, end, and delete them, and mark a prize as handed over.
 *
 * @author CJ
 */

@RestController
@RequestMapping("/api/challenges")
public class ChallengeController {

    private final ChallengeService challengeService;

    @Autowired
    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @PreAuthorize(Permissions.SEE_CHALLENGES)                           // Every role (D6)
    @GetMapping                                                         // Get the running, coming, and recently ended challenges, with everyone's progress
    public ResponseEntity<List<ChallengeView>> getChallenges() {
        return ResponseEntity.ok(challengeService.getChallenges());
    }

    @PreAuthorize(Permissions.MANAGE_CHALLENGES)
    @PostMapping                                                        // Make a challenge. Who made it is the signed-in manager.
    public ResponseEntity<ChallengeView> createChallenge(@Valid @RequestBody ChallengeRequest request,
                                                         @AuthenticationPrincipal StaffSession currentStaff) {
        return ResponseEntity.status(HttpStatus.CREATED).body(challengeService.create(request, currentStaff));
    }

    @PreAuthorize(Permissions.MANAGE_CHALLENGES)
    @PutMapping("/{id}")                                                // Change a challenge (refused once a prize has been marked awarded on it)
    public ResponseEntity<ChallengeView> changeChallenge(@PathVariable Long id,
                                                         @Valid @RequestBody ChallengeRequest request,
                                                         @AuthenticationPrincipal StaffSession currentStaff) {
        return ResponseEntity.ok(challengeService.change(id, request, currentStaff));
    }

    @PreAuthorize(Permissions.MANAGE_CHALLENGES)
    @PostMapping("/{id}/end")                                           // End a running challenge now
    public ResponseEntity<ChallengeView> endChallenge(@PathVariable Long id,
                                                      @AuthenticationPrincipal StaffSession currentStaff) {
        return ResponseEntity.ok(challengeService.endNow(id, currentStaff));
    }

    @PreAuthorize(Permissions.MANAGE_CHALLENGES)
    @DeleteMapping("/{id}")                                             // Delete a challenge (refused once a prize has been marked awarded on it)
    public ResponseEntity<Void> deleteChallenge(@PathVariable Long id,
                                                @AuthenticationPrincipal StaffSession currentStaff) {
        challengeService.delete(id, currentStaff);
        return ResponseEntity.noContent().build();
    }

    @PreAuthorize(Permissions.MANAGE_CHALLENGES)
    @PostMapping("/{id}/awards/{staffId}")                              // Record that this person was handed the challenge's prize
    public ResponseEntity<ChallengeView> markAwarded(@PathVariable Long id,
                                                     @PathVariable Long staffId,
                                                     @AuthenticationPrincipal StaffSession currentStaff) {
        return ResponseEntity.ok(challengeService.markAwarded(id, staffId, currentStaff));
    }
}
