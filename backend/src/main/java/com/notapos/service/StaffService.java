package com.notapos.service;

import com.notapos.dto.SignInResponse;
import com.notapos.dto.StaffCreateRequest;
import com.notapos.dto.StaffUpdateRequest;
import com.notapos.dto.StaffView;
import com.notapos.entity.Staff;
import com.notapos.enums.StaffRole;
import com.notapos.exception.NotSignedInException;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.exception.StaffRuleException;
import com.notapos.exception.TooManyAttemptsException;
import com.notapos.repository.StaffRepository;
import com.notapos.security.StaffSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for Staff operations: PIN sign-in, and managing who works here.
 *
 * HOW PINS ARE STORED
 *   A PIN is never saved. What is saved is its BCrypt "hash": a scrambled form
 *   that a typed PIN can be checked against, but that cannot be turned back
 *   into the PIN. BCrypt also mixes a random "salt" into every hash, so two
 *   people with the same PIN would still have different hashes.
 *
 * HOW SIGN-IN WORKS
 *   Like Toast, a PIN is all a person types - no name. So a PIN has to belong
 *   to exactly one active person, and sign-in checks the typed PIN against
 *   each active staff member's hash until one matches.
 *
 * WHY THERE IS A LIMIT ON WRONG PINS
 *   A 4-digit PIN has only 10,000 possibilities. Without a limit, a script
 *   could try them all in a minute. After 5 wrong PINs within a minute,
 *   sign-in pauses for everyone for 30 seconds.
 *
 * @author CJ
 */
@Service
@Transactional(readOnly = true)      // Every method runs in a database transaction; read-only unless a method says otherwise
public class StaffService {

    private static final Logger log = LoggerFactory.getLogger(StaffService.class);

    // The first manager, created when the staff table is empty (see ensureFirstManager)
    static final String FIRST_MANAGER_NAME = "Manager";
    static final String FIRST_MANAGER_PIN = "1234";

    // Wrong-PIN limit: this many failures within the window pauses sign-in for the lockout time
    static final int MAX_FAILED_ATTEMPTS = 5;
    static final Duration FAILED_ATTEMPT_WINDOW = Duration.ofMinutes(1);
    static final Duration LOCKOUT = Duration.ofSeconds(30);

    private final StaffRepository staffRepository;
    private final StaffSessionStore sessions;

    // Strength 6 keeps one check to about a millisecond. Sign-in compares the PIN with
    // every active person's hash, so a slow setting would make the PIN pad feel stuck.
    // (For a 4-digit PIN the real protection is the wrong-PIN limit, not a slow hash.)
    private final BCryptPasswordEncoder pinEncoder = new BCryptPasswordEncoder(6);

    // When recent wrong PINs were typed, oldest first. Guarded by "synchronized" below
    // because several screens can be signing in at the same moment.
    private final Deque<Instant> recentFailures = new ArrayDeque<>();
    private Instant lockedUntil = Instant.MIN;

    @Autowired
    public StaffService(StaffRepository staffRepository, StaffSessionStore sessions) {
        this.staffRepository = staffRepository;
        this.sessions = sessions;
    }

    // ------------------------------------------------------------------
    // Signing in and out
    // ------------------------------------------------------------------

    public SignInResponse signIn(String pin) {                      // Check a PIN and start a session for whoever it belongs to
        refuseIfLockedOut();

        Optional<Staff> match = findActiveStaffByPin(pin);
        if (match.isEmpty()) {
            recordFailedAttempt();
            throw new NotSignedInException("PIN not recognized.");
        }

        Staff staff = match.get();
        StaffRole role = StaffRole.fromValue(staff.getRole()).orElse(StaffRole.SERVER);
        String token = sessions.createSession(staff.getStaffId(), staff.getName(), role);
        return new SignInResponse(toView(staff), token);
    }

    public void signOut(String token) {                             // End a session
        sessions.endSession(token);
    }

    private Optional<Staff> findActiveStaffByPin(String pin) {
        return staffRepository.findByIsActive(true).stream()
                .filter(staff -> pinEncoder.matches(pin, staff.getPinHash()))
                .findFirst();
    }

    private synchronized void refuseIfLockedOut() {
        if (Instant.now().isBefore(lockedUntil)) {
            throw new TooManyAttemptsException("Too many wrong PINs. Wait 30 seconds and try again.");
        }
    }

    private synchronized void recordFailedAttempt() {
        Instant now = Instant.now();
        recentFailures.addLast(now);

        // Forget failures older than the window
        while (!recentFailures.isEmpty() && recentFailures.peekFirst().isBefore(now.minus(FAILED_ATTEMPT_WINDOW))) {
            recentFailures.removeFirst();
        }

        if (recentFailures.size() >= MAX_FAILED_ATTEMPTS) {
            lockedUntil = now.plus(LOCKOUT);
            recentFailures.clear();
            log.warn("{} wrong PINs within a minute - sign-in paused for {} seconds", MAX_FAILED_ATTEMPTS, LOCKOUT.toSeconds());
        }
    }

    // ------------------------------------------------------------------
    // Managing staff
    // ------------------------------------------------------------------

    public List<StaffView> getAllStaff() {                          // Everyone, active or not, in name order
        return staffRepository.findAllByOrderByNameAsc().stream().map(this::toView).toList();
    }

    @Transactional
    public StaffView createStaff(StaffCreateRequest request) {      // Add a staff member
        StaffRole role = parseRole(request.role());
        refuseIfPinInUse(request.pin(), null);

        Staff staff = new Staff();
        staff.setName(request.name().trim());
        staff.setRole(role.getValue());
        staff.setPinHash(pinEncoder.encode(request.pin()));
        staff.setIsActive(true);

        return toView(staffRepository.save(staff));
    }

    /**
     * Change a staff member. Only the fields that are present in the request change.
     *
     * Three rules keep sign-in sound:
     *   - a PIN can belong to only one active person
     *   - bringing back someone who was deactivated needs a new PIN
     *   - the last active manager cannot be deactivated or given another role,
     *     because then nobody could manage staff at all
     *
     * Anything that changes what a person may do (role, PIN, active) also signs
     * them out everywhere, so the change takes effect immediately.
     */
    @Transactional
    public StaffView updateStaff(Long id, StaffUpdateRequest request) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with id: " + id));

        boolean wasActiveManager = Boolean.TRUE.equals(staff.getIsActive()) && StaffRole.MANAGER.matches(staff.getRole());
        boolean accessChanged = false;

        if (request.name() != null) {
            staff.setName(request.name().trim());
        }

        if (request.role() != null) {
            StaffRole newRole = parseRole(request.role());
            accessChanged |= !newRole.matches(staff.getRole());
            staff.setRole(newRole.getValue());
        }

        boolean beingReactivated = Boolean.TRUE.equals(request.isActive()) && !Boolean.TRUE.equals(staff.getIsActive());
        if (beingReactivated && request.pin() == null) {
            // While they were inactive their old PIN may have been given to someone else. Two
            // hashes cannot be compared with each other to find out, so a fresh PIN is required.
            throw new StaffRuleException("Set a new PIN to bring " + staff.getName() + " back.");
        }

        if (request.isActive() != null) {
            accessChanged |= !request.isActive().equals(staff.getIsActive());
            staff.setIsActive(request.isActive());
        }

        boolean isActiveManagerNow = Boolean.TRUE.equals(staff.getIsActive()) && StaffRole.MANAGER.matches(staff.getRole());
        if (wasActiveManager && !isActiveManagerNow
                && staffRepository.countByRoleAndIsActive(StaffRole.MANAGER.getValue(), true) <= 1) {
            throw new StaffRuleException(staff.getName() + " is the only manager. Make someone else a manager first.");
        }

        if (request.pin() != null) {
            refuseIfPinInUse(request.pin(), id);
            staff.setPinHash(pinEncoder.encode(request.pin()));
            accessChanged = true;
        }

        Staff saved = staffRepository.save(staff);
        if (accessChanged) {
            sessions.endSessionsFor(id);
        }
        return toView(saved);
    }

    /**
     * Make sure somebody can sign in. Called once when the backend starts.
     *
     * If the staff table is empty there is nobody to sign in as, and so nobody
     * who could add the first staff member. This creates one manager with a
     * well-known PIN and says so loudly in the log. The first thing to do with
     * it is sign in and change that PIN.
     *
     * @return true if the first manager was created just now
     */
    @Transactional
    public boolean ensureFirstManager() {
        if (staffRepository.count() > 0) {
            return false;
        }

        Staff manager = new Staff();
        manager.setName(FIRST_MANAGER_NAME);
        manager.setRole(StaffRole.MANAGER.getValue());
        manager.setPinHash(pinEncoder.encode(FIRST_MANAGER_PIN));
        manager.setIsActive(true);
        staffRepository.save(manager);

        log.warn("No staff existed, so a first manager was created. Sign in with PIN {} and change it on the Staff screen.",
                FIRST_MANAGER_PIN);
        return true;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private StaffRole parseRole(String role) {
        return StaffRole.fromValue(role)
                .orElseThrow(() -> new StaffRuleException("\"" + role + "\" is not a role. Use server, bartender, manager, host, or kitchen."));
    }

    // A PIN identifies one person, so no two active people may share one. exceptStaffId is the
    // person being edited (their own current PIN does not count as "in use").
    private void refuseIfPinInUse(String pin, Long exceptStaffId) {
        boolean inUse = staffRepository.findByIsActive(true).stream()
                .filter(other -> !other.getStaffId().equals(exceptStaffId))
                .anyMatch(other -> pinEncoder.matches(pin, other.getPinHash()));
        if (inUse) {
            throw new StaffRuleException("That PIN is already in use. Pick a different one.");
        }
    }

    private StaffView toView(Staff staff) {
        return new StaffView(staff.getStaffId(), staff.getName(), staff.getRole(), staff.getIsActive());
    }
}
