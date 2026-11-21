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
import com.notapos.security.SignInGuard;
import com.notapos.security.StaffSession;
import com.notapos.security.StaffSessionStore;
import com.notapos.testsupport.InMemoryStaffSessionStore;
import com.notapos.testsupport.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StaffService.
 *
 * Tests PIN sign-in, choosing a new PIN, the starter PIN, and the rules for
 * managing staff.
 *
 * Two collaborators are real objects, not mocks, so a test can check what
 * really happened instead of which methods were called:
 *   - the session keeper: the in-memory StaffSessionStore (the database one
 *     has its own tests), so a sign-in can be shown to produce a working session
 *   - the wrong-PIN guard (SignInGuard), on a clock the test moves by hand.
 *     The counting rules themselves are tested in SignInGuardTest; here the
 *     tests check that StaffService reports to the guard correctly.
 *
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

    @Mock
    private StaffRepository staffRepository;

    private StaffSessionStore sessions;
    private MutableClock clock;
    private StaffService staffService;

    // Two devices a request can come from
    private static final String BAR = "192.168.1.21";
    private static final String PATIO = "192.168.1.22";

    // Makes PIN hashes for the test staff, the same way the service does
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(6);

    private Staff sarah;        // server, PIN 1111
    private Staff morgan;       // manager, PIN 2222

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        sessions = new InMemoryStaffSessionStore(clock);
        staffService = new StaffService(staffRepository, sessions, new SignInGuard(clock));

        sarah = staff(1L, "Sarah", "server", "1111", true);
        morgan = staff(2L, "Morgan", "manager", "2222", true);
    }

    private Staff staff(Long id, String name, String role, String pin, boolean active) {
        Staff staff = new Staff();
        staff.setStaffId(id);
        staff.setName(name);
        staff.setRole(role);
        staff.setPinHash(encoder.encode(pin));
        staff.setIsActive(active);
        return staff;
    }

    // ------------------------------------------------------------------
    // Signing in
    // ------------------------------------------------------------------

    @Test
    void testSignIn_WithTheRightPin_ShouldReturnThePersonAndAWorkingSession() {
        // WHAT: Test signing in with a PIN that belongs to someone
        // WHY: A PIN is all a person types - it must find exactly them

        // Given
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, morgan));

        // When
        SignInResponse response = staffService.signIn("2222", BAR);

        // Then - Morgan, with a token that really leads to a manager session
        assertEquals("Morgan", response.staff().name());
        assertEquals("manager", response.staff().role());
        assertEquals(StaffRole.MANAGER, sessions.sessionFor(response.token()).orElseThrow().role());
        assertEquals(2L, sessions.sessionFor(response.token()).orElseThrow().staffId());
    }

    @Test
    void testSignIn_WithAPinNobodyHas_ShouldThrowAndStartNoSession() {
        // WHAT: Test signing in with a wrong PIN
        // WHY: Becomes a 401; the PIN pad says "PIN not recognized"

        // Given
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, morgan));

        // When & Then
        NotSignedInException exception = assertThrows(NotSignedInException.class, () -> staffService.signIn("9999", BAR));
        assertEquals("PIN not recognized.", exception.getMessage());
    }

    @Test
    void testSignIn_ShouldOnlyLookAtActiveStaff() {
        // WHAT: Test that sign-in asks for active staff only
        // WHY: Someone who no longer works here must not be able to sign in with their old PIN

        // Given - nobody active has this PIN (the person who had it was deactivated)
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(morgan));

        // When & Then
        assertThrows(NotSignedInException.class, () -> staffService.signIn("1111", BAR));
        verify(staffRepository, never()).findAll();
        verify(staffRepository, never()).findByIsActive(false);
    }

    @Test
    void testSignIn_AfterFiveWrongPinsOnADevice_ShouldPauseThatDeviceEvenForTheRightPin() {
        // WHAT: Test the wrong-PIN limit
        // WHY: A 4-digit PIN has 10,000 possibilities. Without a limit a script could
        //      try them all in under a minute.

        // Given
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, morgan));

        // When - five wrong PINs in a row at the bar
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(NotSignedInException.class, () -> staffService.signIn("9999", BAR));
        }

        // Then - the sixth try there is refused outright, even though the PIN is correct
        TooManyAttemptsException exception = assertThrows(TooManyAttemptsException.class, () -> staffService.signIn("2222", BAR));
        assertEquals("Too many wrong PINs on this device. Try again in 30 seconds.", exception.getMessage());
        assertEquals(30, exception.getRetryAfterSeconds());
    }

    @Test
    void testSignIn_WhileOneDeviceIsPaused_ShouldStillWorkOnAnother() {
        // WHAT: Test that wrong PINs are counted for the device they came from
        // WHY: The count used to be one number for the whole restaurant: five wrong PINs
        //      at the bar stopped the patio from signing anyone in.

        // Given - the bar is paused
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, morgan));
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(NotSignedInException.class, () -> staffService.signIn("9999", BAR));
        }

        // Then
        assertEquals("Morgan", staffService.signIn("2222", PATIO).staff().name());
    }

    @Test
    void testSignIn_WhileADeviceIsPaused_ShouldNotLookAtThePin() {
        // WHAT: Test that a paused device's PINs are not checked at all
        // WHY: If they were, the answer ("paused" for a wrong PIN, something else for a
        //      right one) could leak, and a pause would not slow guessing down.

        // Given - the bar is paused
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, morgan));
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(NotSignedInException.class, () -> staffService.signIn("9999", BAR));
        }
        clearInvocations(staffRepository);

        // When
        assertThrows(TooManyAttemptsException.class, () -> staffService.signIn("2222", BAR));

        // Then - nobody's hash was read
        verifyNoInteractions(staffRepository);
    }

    @Test
    void testSignIn_AFewWrongPinsThenTheRightOne_ShouldStillWork() {
        // WHAT: Test that ordinary mistakes do not lock anyone out
        // WHY: People mistype. Only a run of five failures pauses sign-in.

        // Given
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, morgan));

        // When - four wrong, then right
        for (int attempt = 0; attempt < 4; attempt++) {
            assertThrows(NotSignedInException.class, () -> staffService.signIn("9999", BAR));
        }

        // Then
        assertEquals("Sarah", staffService.signIn("1111", BAR).staff().name());
    }

    @Test
    void testSignIn_WhenTheDatabaseFails_ShouldNotCountAsAWrongPin() {
        // WHAT: Test sign-in while the staff table cannot be read
        // WHY: Nobody typed a wrong PIN. If these counted, a database hiccup during a
        //      rush would pause every terminal that tried to sign in through it.

        // Given - the database fails six times
        when(staffRepository.findByIsActive(true)).thenThrow(new IllegalStateException("database is down"));
        for (int attempt = 0; attempt < 6; attempt++) {
            assertThrows(IllegalStateException.class, () -> staffService.signIn("1111", BAR));
        }

        // When - it comes back
        reset(staffRepository);
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah));

        // Then - the bar is not paused
        assertEquals("Sarah", staffService.signIn("1111", BAR).staff().name());
    }

    @Test
    void testSignIn_WithTheStarterPinStillInPlace_ShouldStartASessionThatMustChangeIt() {
        // WHAT: Test signing in as someone marked "must change PIN"
        // WHY: The answer tells the screen to ask for a new PIN, and the session tells
        //      the backend to refuse everything else until one is chosen

        // Given
        Staff firstManager = staff(3L, "Manager", "manager", "1234", true);
        firstManager.setMustChangePin(true);
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(firstManager));

        // When
        SignInResponse response = staffService.signIn("1234", BAR);

        // Then
        assertTrue(response.staff().mustChangePin());
        assertTrue(sessions.sessionFor(response.token()).orElseThrow().mustChangePin());
    }

    @Test
    void testSignOut_ShouldEndTheSession() {
        // WHAT: Test signing out
        // WHY: The token must stop working the moment someone signs out

        // Given
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah));
        String token = staffService.signIn("1111", BAR).token();

        // When
        staffService.signOut(token);

        // Then
        assertTrue(sessions.sessionFor(token).isEmpty());
    }

    // ------------------------------------------------------------------
    // Choosing a new PIN for yourself
    // ------------------------------------------------------------------

    // Sign a person in at the bar and hand back their session and token
    private String signInAtTheBar(Staff staff, String pin) {
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, morgan, staff).stream().distinct().toList());
        lenient().when(staffRepository.findById(staff.getStaffId())).thenReturn(Optional.of(staff));
        lenient().when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return staffService.signIn(pin, BAR).token();
    }

    @Test
    void testChangeOwnPin_ShouldSetTheNewPinAndKeepThisDeviceSignedIn() {
        // WHAT: Test the first manager choosing a PIN of their own
        // WHY: This is the one thing they may do. Afterwards the starter PIN must be dead,
        //      the flag cleared, and the device they used must carry on without signing in again.

        // Given - the first manager, signed in at the bar and (earlier) at the patio
        Staff firstManager = staff(3L, "Manager", "manager", "1234", true);
        firstManager.setMustChangePin(true);
        String patioToken = signInAtTheBar(firstManager, "1234");
        String barToken = signInAtTheBar(firstManager, "1234");
        StaffSession who = sessions.sessionFor(barToken).orElseThrow();

        // When
        StaffView changed = staffService.changeOwnPin(who, barToken, "1234", "4821", BAR);

        // Then - the new PIN is stored, the starter PIN is not, nothing is left to change
        assertFalse(changed.mustChangePin());
        assertFalse(firstManager.getMustChangePin());
        assertTrue(encoder.matches("4821", firstManager.getPinHash()));
        assertFalse(encoder.matches("1234", firstManager.getPinHash()));

        // And - this device carries on, free to do everything; the other device is signed out
        assertFalse(sessions.sessionFor(barToken).orElseThrow().mustChangePin());
        assertTrue(sessions.sessionFor(patioToken).isEmpty());
    }

    @Test
    void testChangeOwnPin_WithTheWrongCurrentPin_ShouldChangeNothing() {
        // WHAT: Test a PIN change by someone who does not know the current PIN
        // WHY: A terminal left signed in must not let a passer-by take the sign-in over.
        //      Knowing the current PIN is the proof that it is still the same person.

        // Given
        String token = signInAtTheBar(sarah, "1111");
        StaffSession who = sessions.sessionFor(token).orElseThrow();

        // When & Then
        StaffRuleException exception = assertThrows(StaffRuleException.class,
                () -> staffService.changeOwnPin(who, token, "9999", "4821", BAR));
        assertEquals("That is not your current PIN.", exception.getMessage());
        assertTrue(encoder.matches("1111", sarah.getPinHash()));
        verify(staffRepository, never()).save(any());
    }

    @Test
    void testChangeOwnPin_WrongCurrentPinFiveTimes_ShouldPauseTheDevice() {
        // WHAT: Test guessing the current PIN on the change-PIN screen
        // WHY: It checks a PIN, so it is a place to guess one. It must count against the
        //      same limit as the PIN pad, or the limit could be walked around here.

        // Given
        String token = signInAtTheBar(sarah, "1111");
        StaffSession who = sessions.sessionFor(token).orElseThrow();

        // When - five wrong guesses
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThrows(StaffRuleException.class, () -> staffService.changeOwnPin(who, token, "9999", "4821", BAR));
        }

        // Then - the sixth is refused even with the right current PIN, and so is sign-in there
        assertThrows(TooManyAttemptsException.class, () -> staffService.changeOwnPin(who, token, "1111", "4821", BAR));
        assertThrows(TooManyAttemptsException.class, () -> staffService.signIn("1111", BAR));
        assertTrue(encoder.matches("1111", sarah.getPinHash()));
    }

    @Test
    void testChangeOwnPin_ToAPinThatIsNotAllowed_ShouldThrowAndChangeNothing() {
        // WHAT: Test the three new PINs that are refused
        // WHY: The starter PIN is the first anyone would guess; the same PIN again is no
        //      change; and a PIN someone else holds would sign one of them in as the other.

        // Given
        String token = signInAtTheBar(sarah, "1111");
        StaffSession who = sessions.sessionFor(token).orElseThrow();

        // When & Then
        assertEquals("1234 is the starter PIN and cannot be used. Pick a different one.",
                assertThrows(StaffRuleException.class, () -> staffService.changeOwnPin(who, token, "1111", "1234", BAR)).getMessage());
        assertEquals("The new PIN must be different from the current one.",
                assertThrows(StaffRuleException.class, () -> staffService.changeOwnPin(who, token, "1111", "1111", BAR)).getMessage());
        assertEquals("That PIN is already in use. Pick a different one.",
                assertThrows(StaffRuleException.class, () -> staffService.changeOwnPin(who, token, "1111", "2222", BAR)).getMessage());

        assertTrue(encoder.matches("1111", sarah.getPinHash()));
        verify(staffRepository, never()).save(any());
        assertTrue(sessions.sessionFor(token).isPresent());
    }

    // ------------------------------------------------------------------
    // Adding staff
    // ------------------------------------------------------------------

    @Test
    void testCreateStaff_WithTheStarterPin_ShouldThrowAndSaveNothing() {
        // WHAT: Test adding someone with the PIN 1234
        // WHY: It is the PIN every new database starts with. Nobody may hold it.

        StaffRuleException exception = assertThrows(StaffRuleException.class,
                () -> staffService.createStaff(new StaffCreateRequest("Alex", "server", "1234")));

        assertEquals("1234 is the starter PIN and cannot be used. Pick a different one.", exception.getMessage());
        verify(staffRepository, never()).save(any());
    }

    @Test
    void testCreateStaff_ShouldStoreAHashAndNeverThePin() {
        // WHAT: Test adding a staff member
        // WHY: If the database ever leaked, PINs stored as typed would be readable by anyone

        // Given
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, morgan));
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        StaffView created = staffService.createStaff(new StaffCreateRequest("  Alex  ", "bartender", "3333"));

        // Then - saved trimmed, active, with a hash that matches the PIN but is not the PIN
        ArgumentCaptor<Staff> saved = ArgumentCaptor.forClass(Staff.class);
        verify(staffRepository).save(saved.capture());
        assertEquals("Alex", saved.getValue().getName());
        assertEquals("bartender", saved.getValue().getRole());
        assertTrue(saved.getValue().getIsActive());
        assertNotEquals("3333", saved.getValue().getPinHash());
        assertTrue(encoder.matches("3333", saved.getValue().getPinHash()));
        assertEquals("Alex", created.name());
    }

    @Test
    void testCreateStaff_WithAPinSomeoneAlreadyHas_ShouldThrowAndSaveNothing() {
        // WHAT: Test adding a staff member with a PIN that is taken
        // WHY: A PIN identifies one person. Two people with the same PIN would sign in as each other.

        // Given - Sarah already has 1111
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, morgan));

        // When & Then
        StaffRuleException exception = assertThrows(StaffRuleException.class,
                () -> staffService.createStaff(new StaffCreateRequest("Alex", "server", "1111")));
        assertTrue(exception.getMessage().contains("already in use"));
        verify(staffRepository, never()).save(any());
    }

    @Test
    void testCreateStaff_WithARoleThatDoesNotExist_ShouldThrowAndSaveNothing() {
        // WHAT: Test adding a staff member with a made-up role
        // WHY: A role decides what someone can do; an unknown one must not get in

        assertThrows(StaffRuleException.class,
                () -> staffService.createStaff(new StaffCreateRequest("Alex", "owner", "3333")));
        verify(staffRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Changing staff
    // ------------------------------------------------------------------

    @Test
    void testUpdateStaff_ShouldChangeOnlyTheFieldsThatWereSent() {
        // WHAT: Test changing only someone's name
        // WHY: The screen sends just what the manager edited; everything else must stay

        // Given
        String hashBefore = sarah.getPinHash();
        when(staffRepository.findById(1L)).thenReturn(Optional.of(sarah));
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        StaffView updated = staffService.updateStaff(1L, new StaffUpdateRequest("Sarah M.", null, null, null));

        // Then
        assertEquals("Sarah M.", updated.name());
        assertEquals("server", updated.role());
        assertTrue(updated.isActive());
        assertEquals(hashBefore, sarah.getPinHash());
    }

    @Test
    void testUpdateStaff_ChangingThePin_ShouldSignThatPersonOut() {
        // WHAT: Test giving someone a new PIN while they are signed in
        // WHY: The change must take effect right away, on every screen

        // Given - Sarah is signed in
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, morgan));
        String token = staffService.signIn("1111", BAR).token();
        when(staffRepository.findById(1L)).thenReturn(Optional.of(sarah));
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        staffService.updateStaff(1L, new StaffUpdateRequest(null, null, "4444", null));

        // Then - her session is gone, the new PIN works, the old one does not
        assertTrue(sessions.sessionFor(token).isEmpty());
        assertTrue(encoder.matches("4444", sarah.getPinHash()));
        assertFalse(encoder.matches("1111", sarah.getPinHash()));
    }

    @Test
    void testUpdateStaff_ChangingOnlyTheName_ShouldNotSignThemOut() {
        // WHAT: Test that fixing a typo in a name leaves the person signed in
        // WHY: Only changes to what someone may do should interrupt them mid-shift

        // Given
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah));
        String token = staffService.signIn("1111", BAR).token();
        when(staffRepository.findById(1L)).thenReturn(Optional.of(sarah));
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        staffService.updateStaff(1L, new StaffUpdateRequest("Sara", null, null, null));

        // Then - still signed in, and under the corrected name (so the orders she opens carry it)
        assertEquals("Sara", sessions.sessionFor(token).orElseThrow().name());
    }

    @Test
    void testUpdateStaff_ToTheStarterPin_ShouldThrowAndChangeNothing() {
        // WHAT: Test a manager giving someone the PIN 1234
        // WHY: Nobody may hold the starter PIN, whoever sets it

        // Given
        when(staffRepository.findById(1L)).thenReturn(Optional.of(sarah));

        // When & Then
        assertThrows(StaffRuleException.class,
                () -> staffService.updateStaff(1L, new StaffUpdateRequest(null, null, "1234", null)));
        assertTrue(encoder.matches("1111", sarah.getPinHash()));
        verify(staffRepository, never()).save(any());
    }

    @Test
    void testUpdateStaff_GivingANewPinToSomeoneWhoMustChangeTheirs_ShouldClearTheFlag() {
        // WHAT: Test a manager setting a PIN for a person still marked "must change PIN"
        // WHY: The flag means "holds the starter PIN". Once any other PIN is set that is
        //      no longer true, and asking them to change it again would be for nothing.

        // Given - a second manager who still holds the starter PIN
        Staff starter = staff(3L, "Manager", "manager", "1234", true);
        starter.setMustChangePin(true);
        when(staffRepository.findById(3L)).thenReturn(Optional.of(starter));
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, morgan, starter));
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        StaffView changed = staffService.updateStaff(3L, new StaffUpdateRequest(null, null, "4821", null));

        // Then
        assertFalse(changed.mustChangePin());
        assertFalse(starter.getMustChangePin());
    }

    @Test
    void testUpdateStaff_ToAPinSomeoneElseHas_ShouldThrow() {
        // WHAT: Test changing a PIN to one that is taken
        // WHY: Same rule as when adding someone

        // Given - Morgan has 2222
        when(staffRepository.findById(1L)).thenReturn(Optional.of(sarah));
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, morgan));

        // When & Then
        assertThrows(StaffRuleException.class,
                () -> staffService.updateStaff(1L, new StaffUpdateRequest(null, null, "2222", null)));
        verify(staffRepository, never()).save(any());
    }

    @Test
    void testUpdateStaff_KeepingTheirOwnPin_ShouldBeAllowed() {
        // WHAT: Test "changing" a PIN to the one the person already has
        // WHY: Their own PIN is not "in use by someone else"

        // Given
        when(staffRepository.findById(1L)).thenReturn(Optional.of(sarah));
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, morgan));
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When & Then
        assertDoesNotThrow(() -> staffService.updateStaff(1L, new StaffUpdateRequest(null, null, "1111", null)));
    }

    @Test
    void testUpdateStaff_DeactivatingTheOnlyManager_ShouldThrow() {
        // WHAT: Test deactivating the last manager
        // WHY: With no manager, nobody could add staff, reset a PIN, or see the Financials ever again

        // Given - Morgan is the only active manager
        when(staffRepository.findById(2L)).thenReturn(Optional.of(morgan));
        when(staffRepository.countByRoleAndIsActive("manager", true)).thenReturn(1L);

        // When & Then
        StaffRuleException exception = assertThrows(StaffRuleException.class,
                () -> staffService.updateStaff(2L, new StaffUpdateRequest(null, null, null, false)));
        assertTrue(exception.getMessage().contains("only manager"));
        verify(staffRepository, never()).save(any());
    }

    @Test
    void testUpdateStaff_DemotingTheOnlyManager_ShouldThrow() {
        // WHAT: Test giving the last manager a different role
        // WHY: Same lock-out as deactivating them

        // Given
        when(staffRepository.findById(2L)).thenReturn(Optional.of(morgan));
        when(staffRepository.countByRoleAndIsActive("manager", true)).thenReturn(1L);

        // When & Then
        assertThrows(StaffRuleException.class,
                () -> staffService.updateStaff(2L, new StaffUpdateRequest(null, "server", null, null)));
        verify(staffRepository, never()).save(any());
    }

    @Test
    void testUpdateStaff_DeactivatingAManagerWhenThereIsAnother_ShouldBeAllowed() {
        // WHAT: Test deactivating one of two managers
        // WHY: The rule protects the LAST manager only

        // Given
        when(staffRepository.findById(2L)).thenReturn(Optional.of(morgan));
        when(staffRepository.countByRoleAndIsActive("manager", true)).thenReturn(2L);
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        StaffView updated = staffService.updateStaff(2L, new StaffUpdateRequest(null, null, null, false));

        // Then
        assertFalse(updated.isActive());
    }

    @Test
    void testUpdateStaff_ReactivatingWithoutANewPin_ShouldThrow() {
        // WHAT: Test bringing back someone who was deactivated, without setting a PIN
        // WHY: Their old PIN may have been given to someone else in the meantime, and two
        //      hashes cannot be compared to find out. A fresh PIN is the safe answer.

        // Given
        Staff former = staff(3L, "Jamie", "server", "5555", false);
        when(staffRepository.findById(3L)).thenReturn(Optional.of(former));

        // When & Then
        StaffRuleException exception = assertThrows(StaffRuleException.class,
                () -> staffService.updateStaff(3L, new StaffUpdateRequest(null, null, null, true)));
        assertTrue(exception.getMessage().contains("new PIN"));

        // And with a new PIN it works
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, morgan));
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));
        assertTrue(staffService.updateStaff(3L, new StaffUpdateRequest(null, null, "6666", true)).isActive());
    }

    @Test
    void testUpdateStaff_WhenNotFound_ShouldThrowException() {
        // WHAT: Test changing a staff member who does not exist
        // WHY: Becomes a 404 for the screen

        when(staffRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> staffService.updateStaff(99L, new StaffUpdateRequest("X", null, null, null)));
    }

    @Test
    void testGetAllStaff_ShouldReturnEveryoneInNameOrderWithoutPinHashes() {
        // WHAT: Test listing staff
        // WHY: The Staff screen shows everyone, including people who were deactivated

        // Given
        when(staffRepository.findAllByOrderByNameAsc()).thenReturn(List.of(morgan, sarah));

        // When
        List<StaffView> staff = staffService.getAllStaff();

        // Then - StaffView has no field that could carry a PIN or its hash
        assertEquals(List.of("Morgan", "Sarah"), staff.stream().map(StaffView::name).toList());
        //        (the five it has: who they are, their role, active or not, and whether they must choose a new PIN)
        assertEquals(List.of("staffId", "name", "role", "isActive", "mustChangePin"),
                java.util.Arrays.stream(StaffView.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
    }

    // ------------------------------------------------------------------
    // The first manager
    // ------------------------------------------------------------------

    @Test
    void testEnsureFirstManager_WhenThereAreNoStaff_ShouldCreateOneManager() {
        // WHAT: Test start-up on an empty staff table
        // WHY: With nobody to sign in as, nobody could ever add the first staff member

        // Given
        when(staffRepository.count()).thenReturn(0L);

        // When
        boolean created = staffService.ensureFirstManager();

        // Then - a manager whose PIN is the documented starting PIN
        assertTrue(created);
        ArgumentCaptor<Staff> saved = ArgumentCaptor.forClass(Staff.class);
        verify(staffRepository).save(saved.capture());
        assertEquals("manager", saved.getValue().getRole());
        assertTrue(saved.getValue().getIsActive());
        assertTrue(encoder.matches(StaffService.FIRST_MANAGER_PIN, saved.getValue().getPinHash()));

        // And - marked to choose a PIN of their own before doing anything else
        assertTrue(saved.getValue().getMustChangePin());
    }

    @Test
    void testEnsureFirstManager_WhenStaffExist_ShouldDoNothing() {
        // WHAT: Test start-up when there are already staff
        // WHY: A well-known PIN must never be added to a restaurant that is already set up

        // Given
        when(staffRepository.count()).thenReturn(3L);

        // When
        boolean created = staffService.ensureFirstManager();

        // Then
        assertFalse(created);
        verify(staffRepository, never()).save(any());
    }

    // ------------------------------------------------------------------
    // Someone who still holds the starter PIN (a database from before it had to be changed)
    // ------------------------------------------------------------------

    @Test
    void testRequireChangeOfStarterPin_ShouldMarkAndSignOutWhoeverStillHoldsIt() {
        // WHAT: Test start-up on a database whose manager still signs in with 1234
        // WHY: A migration cannot find that person: only hashes are stored. Checking 1234
        //      against each hash can. They must not carry on with the old sign-in, so their
        //      sessions are ended and the next sign-in leads to choosing a new PIN.

        // Given - an old manager on 1234 who is signed in; Sarah on a PIN of her own
        Staff oldManager = staff(3L, "Manager", "manager", "1234", true);
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, oldManager));
        String managerToken = staffService.signIn("1234", BAR).token();
        String sarahToken = staffService.signIn("1111", PATIO).token();

        // When
        int marked = staffService.requireChangeOfStarterPin();

        // Then
        assertEquals(1, marked);
        assertTrue(oldManager.getMustChangePin());
        assertFalse(sarah.getMustChangePin());
        verify(staffRepository).save(oldManager);
        verify(staffRepository, never()).save(sarah);
        assertTrue(sessions.sessionFor(managerToken).isEmpty());
        assertTrue(sessions.sessionFor(sarahToken).isPresent());
    }

    @Test
    void testRequireChangeOfStarterPin_WhenAlreadyMarkedOrNobodyHoldsIt_ShouldDoNothing() {
        // WHAT: Test start-up when there is nothing to find
        // WHY: This runs at every start. A first manager who is already marked, and has
        //      signed in to choose a PIN, must not be signed out again by a restart.

        // Given - the first manager, already marked and signed in
        Staff firstManager = staff(3L, "Manager", "manager", "1234", true);
        firstManager.setMustChangePin(true);
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, morgan, firstManager));
        String token = staffService.signIn("1234", BAR).token();

        // When
        int marked = staffService.requireChangeOfStarterPin();

        // Then
        assertEquals(0, marked);
        verify(staffRepository, never()).save(any());
        assertTrue(sessions.sessionFor(token).isPresent());
    }
}
