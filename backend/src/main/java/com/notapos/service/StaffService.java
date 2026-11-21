package com.notapos.service;

import com.notapos.dto.PausedDeviceView;
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
import com.notapos.security.SignInGuard;
import com.notapos.security.StaffSession;
import com.notapos.security.StaffSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 *   could try them all in a minute. SignInGuard counts wrong PINs per device
 *   and pauses sign-in where too many come from; every place a PIN is checked
 *   in this class goes through it (signIn and changeOwnPin). A manager can
 *   see which terminals are paused and release one (getPausedDevices and
 *   releasePausedDevice).
 *
 * THE STARTER PIN
 *   A new database gets one manager with PIN 1234 (ensureFirstManager). That
 *   person is marked "must change PIN": they can sign in and then do nothing
 *   but choose a new one. 1234 is refused as anybody's new PIN.
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

    private final StaffRepository staffRepository;
    private final StaffSessionStore sessions;
    private final SignInGuard signInGuard;      // Counts wrong PINs and pauses sign-in (see SignInGuard for the rules)

    // Strength 6 keeps one check to about a millisecond. Sign-in compares the PIN with
    // every active person's hash, so a slow setting would make the PIN pad feel stuck.
    // (For a 4-digit PIN the real protection is the wrong-PIN limit, not a slow hash.)
    private final BCryptPasswordEncoder pinEncoder = new BCryptPasswordEncoder(6);

    @Autowired
    public StaffService(StaffRepository staffRepository, StaffSessionStore sessions, SignInGuard signInGuard) {
        this.staffRepository = staffRepository;
        this.sessions = sessions;
        this.signInGuard = signInGuard;
    }

    // ------------------------------------------------------------------
    // Signing in and out
    // ------------------------------------------------------------------

    /**
     * Check a PIN and start a session for whoever it belongs to.
     *
     * @param pin    The four digits that were typed
     * @param device The address the request came from. Wrong PINs are counted per
     *               device, so one terminal's mistakes do not pause another's.
     * @throws TooManyAttemptsException (429) if sign-in is paused for that device or everywhere
     * @throws NotSignedInException (401) if no active person has that PIN
     */
    @Transactional                                                  // Writes: the new session is a row in the database
    public SignInResponse signIn(String pin, String device) {
        signInGuard.begin(device);                                  // Refuses here if paused; the PIN is then not looked at

        Optional<Staff> match;
        try {
            match = findActiveStaffByPin(pin);
        } catch (RuntimeException failure) {
            signInGuard.endWithoutAnswer(device);                   // The database failed, so nobody knows if the PIN was right
            throw failure;
        }

        if (match.isEmpty()) {
            signInGuard.endWrong(device);
            throw new NotSignedInException("PIN not recognized.");
        }
        signInGuard.endRight(device);

        Staff staff = match.get();
        StaffRole role = StaffRole.fromValue(staff.getRole()).orElse(StaffRole.SERVER);
        String token = sessions.createSession(
                staff.getStaffId(), staff.getName(), role, Boolean.TRUE.equals(staff.getMustChangePin()));
        return new SignInResponse(toView(staff), token);
    }

    @Transactional
    public void signOut(String token) {                             // End a session
        sessions.endSession(token);
    }

    /**
     * A signed-in person chooses a new PIN for themselves.
     *
     * This is the one thing a person holding the starter PIN may do. Anyone else
     * may use it too.
     *
     * The current PIN is asked for again, and a wrong one is counted by the same
     * guard as a wrong PIN at sign-in. Without that, a terminal somebody left
     * signed in would be a place to guess that person's PIN ten thousand times.
     *
     * Afterwards the person is signed out everywhere except on the device they
     * made the change from, which carries on.
     *
     * @param who    The signed-in person (from their session)
     * @param token  The token of the session the change is made from
     * @param device The address the request came from
     */
    @Transactional
    public StaffView changeOwnPin(StaffSession who, String token, String currentPin, String newPin, String device) {
        Staff staff = staffRepository.findById(who.staffId())
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with id: " + who.staffId()));

        signInGuard.begin(device);
        boolean currentPinIsRight = pinEncoder.matches(currentPin, staff.getPinHash());
        if (!currentPinIsRight) {
            signInGuard.endWrong(device);
            throw new StaffRuleException("That is not your current PIN.");
        }
        signInGuard.endRight(device);

        refuseIfStarterPin(newPin);
        if (newPin.equals(currentPin)) {
            throw new StaffRuleException("The new PIN must be different from the current one.");
        }
        refuseIfPinInUse(newPin, staff.getStaffId());

        staff.setPinHash(pinEncoder.encode(newPin));
        staff.setMustChangePin(false);
        Staff saved = staffRepository.save(staff);

        sessions.pinChangedBy(saved.getStaffId(), token);           // Signed out everywhere else; this device carries on
        return toView(saved);
    }

    private Optional<Staff> findActiveStaffByPin(String pin) {
        return staffRepository.findByIsActive(true).stream()
                .filter(staff -> pinEncoder.matches(pin, staff.getPinHash()))
                .findFirst();
    }

    // ------------------------------------------------------------------
    // Terminals paused for wrong PINs (managers only; the owner's decision D11)
    // ------------------------------------------------------------------

    /**
     * The terminals where sign-in is paused right now.
     *
     * This reads SignInGuard's memory, not the database.
     *
     * @param askingFrom The address the manager's own request came from, so the
     *                   list can mark the terminal they are standing at
     */
    public List<PausedDeviceView> getPausedDevices(String askingFrom) {
        return signInGuard.pausedDevices().stream()
                .map(paused -> new PausedDeviceView(
                        paused.device(), paused.secondsLeft(), paused.device().equals(askingFrom)))
                .toList();
    }

    /**
     * Let a paused terminal sign people in again, now, with its wrong PINs forgotten.
     *
     * It signs nobody in. Releasing a terminal that is not paused (its pause ran
     * out a moment ago, say) changes nothing and is not an error: the manager's
     * list can be a few seconds old.
     *
     * Every release is written to the log with who did it. A release is one
     * person overriding a safety measure, so there should be a record of it.
     *
     * @param device     The address of the terminal to release
     * @param releasedBy The manager doing it
     * @param askingFrom The address the manager's request came from
     * @return The paused terminals as they now stand, for the screen to redraw from
     */
    public List<PausedDeviceView> releasePausedDevice(String device, StaffSession releasedBy, String askingFrom) {
        if (signInGuard.release(device)) {
            log.warn("Sign-in pause on {} was released by {} (staff ID {})",
                    device, releasedBy.name(), releasedBy.staffId());
        }
        return getPausedDevices(askingFrom);
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
        refuseIfStarterPin(request.pin());
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
     * Four rules keep sign-in sound:
     *   - a PIN can belong to only one active person
     *   - nobody may be given the starter PIN
     *   - bringing back someone who was deactivated needs a new PIN
     *   - the last active manager cannot be deactivated or given another role,
     *     because then nobody could manage staff at all
     *
     * Anything that changes what a person may do (role, PIN, active) also signs
     * them out everywhere, so the change takes effect immediately. A new name
     * does not sign anyone out; it is written into their open sessions, so the
     * orders they open from now on carry it.
     */
    @Transactional
    public StaffView updateStaff(Long id, StaffUpdateRequest request) {
        Staff staff = staffRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Staff member not found with id: " + id));

        boolean wasActiveManager = Boolean.TRUE.equals(staff.getIsActive()) && StaffRole.MANAGER.matches(staff.getRole());
        boolean accessChanged = false;
        boolean nameChanged = false;

        if (request.name() != null) {
            String newName = request.name().trim();
            nameChanged = !newName.equals(staff.getName());
            staff.setName(newName);
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
            refuseIfStarterPin(request.pin());
            refuseIfPinInUse(request.pin(), id);
            staff.setPinHash(pinEncoder.encode(request.pin()));
            staff.setMustChangePin(false);                          // Whatever they held before, it is not the starter PIN now
            accessChanged = true;
        }

        Staff saved = staffRepository.save(staff);
        if (accessChanged) {
            sessions.endSessionsFor(id);
        } else if (nameChanged) {
            sessions.renameSessionsFor(id, saved.getName());        // They stay signed in, under the corrected name
        }
        return toView(saved);
    }

    /**
     * Make sure somebody can sign in. Called once when the backend starts.
     *
     * If the staff table is empty there is nobody to sign in as, and so nobody
     * who could add the first staff member. This creates one manager with a
     * well-known PIN and says so loudly in the log. That manager is marked
     * "must change PIN": signing in leads straight to choosing a new one, and
     * the backend allows nothing else until that is done.
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
        manager.setMustChangePin(true);
        staffRepository.save(manager);

        log.warn("No staff existed, so a first manager was created. Sign in with PIN {}. You will be asked to choose a new PIN straight away.",
                FIRST_MANAGER_PIN);
        return true;
    }

    /**
     * Find anyone who still holds the starter PIN and mark them "must change PIN".
     * Called once when the backend starts, after ensureFirstManager.
     *
     * WHY: a database that was set up before the starter PIN had to be changed may
     * still have a manager signing in with 1234. A migration cannot find them,
     * because the database holds only a hash of each PIN; this can, by checking
     * 1234 against each hash. They are signed out, and their next sign-in leads to
     * choosing a new PIN.
     *
     * @return How many people were marked just now
     */
    @Transactional
    public int requireChangeOfStarterPin() {
        int marked = 0;
        for (Staff staff : staffRepository.findByIsActive(true)) {
            boolean alreadyMarked = Boolean.TRUE.equals(staff.getMustChangePin());
            if (!alreadyMarked && pinEncoder.matches(FIRST_MANAGER_PIN, staff.getPinHash())) {
                staff.setMustChangePin(true);
                staffRepository.save(staff);
                sessions.endSessionsFor(staff.getStaffId());
                marked++;
                log.warn("{} still signs in with the starter PIN {}. They will be asked to choose a new PIN at their next sign-in.",
                        staff.getName(), FIRST_MANAGER_PIN);
            }
        }
        return marked;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private StaffRole parseRole(String role) {
        return StaffRole.fromValue(role)
                .orElseThrow(() -> new StaffRuleException("\"" + role + "\" is not a role. Use server, bartender, manager, host, or kitchen."));
    }

    // The starter PIN is the same on every new database and is the first one anybody would guess
    private void refuseIfStarterPin(String pin) {
        if (FIRST_MANAGER_PIN.equals(pin)) {
            throw new StaffRuleException(FIRST_MANAGER_PIN + " is the starter PIN and cannot be used. Pick a different one.");
        }
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
        return new StaffView(staff.getStaffId(), staff.getName(), staff.getRole(), staff.getIsActive(),
                Boolean.TRUE.equals(staff.getMustChangePin()));
    }
}
