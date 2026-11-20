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
import com.notapos.security.StaffSessionInterceptor;
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
 * Tests PIN sign-in, the wrong-PIN limit, and the rules for managing staff.
 *
 * The session keeper (StaffSessionInterceptor) is the real one, not a mock:
 * it has no dependencies, and using it lets a test check that a sign-in
 * really produced a working session.
 *
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class StaffServiceTest {

    @Mock
    private StaffRepository staffRepository;

    private StaffSessionInterceptor sessions;
    private StaffService staffService;

    // Makes PIN hashes for the test staff, the same way the service does
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(6);

    private Staff sarah;        // server, PIN 1111
    private Staff morgan;       // manager, PIN 2222

    @BeforeEach
    void setUp() {
        sessions = new StaffSessionInterceptor();
        staffService = new StaffService(staffRepository, sessions);

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
        SignInResponse response = staffService.signIn("2222");

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
        NotSignedInException exception = assertThrows(NotSignedInException.class, () -> staffService.signIn("9999"));
        assertEquals("PIN not recognized.", exception.getMessage());
    }

    @Test
    void testSignIn_ShouldOnlyLookAtActiveStaff() {
        // WHAT: Test that sign-in asks for active staff only
        // WHY: Someone who no longer works here must not be able to sign in with their old PIN

        // Given - nobody active has this PIN (the person who had it was deactivated)
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(morgan));

        // When & Then
        assertThrows(NotSignedInException.class, () -> staffService.signIn("1111"));
        verify(staffRepository, never()).findAll();
        verify(staffRepository, never()).findByIsActive(false);
    }

    @Test
    void testSignIn_AfterFiveWrongPins_ShouldPauseSignInEvenForTheRightPin() {
        // WHAT: Test the wrong-PIN limit
        // WHY: A 4-digit PIN has 10,000 possibilities. Without a limit a script could
        //      try them all in under a minute.

        // Given
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, morgan));

        // When - five wrong PINs in a row
        for (int attempt = 0; attempt < StaffService.MAX_FAILED_ATTEMPTS; attempt++) {
            assertThrows(NotSignedInException.class, () -> staffService.signIn("9999"));
        }

        // Then - the sixth try is refused outright, even though the PIN is correct
        TooManyAttemptsException exception = assertThrows(TooManyAttemptsException.class, () -> staffService.signIn("2222"));
        assertTrue(exception.getMessage().contains("30 seconds"));
    }

    @Test
    void testSignIn_AFewWrongPinsThenTheRightOne_ShouldStillWork() {
        // WHAT: Test that ordinary mistakes do not lock anyone out
        // WHY: People mistype. Only a run of five failures pauses sign-in.

        // Given
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah, morgan));

        // When - four wrong, then right
        for (int attempt = 0; attempt < StaffService.MAX_FAILED_ATTEMPTS - 1; attempt++) {
            assertThrows(NotSignedInException.class, () -> staffService.signIn("9999"));
        }

        // Then
        assertEquals("Sarah", staffService.signIn("1111").staff().name());
    }

    @Test
    void testSignOut_ShouldEndTheSession() {
        // WHAT: Test signing out
        // WHY: The token must stop working the moment someone signs out

        // Given
        when(staffRepository.findByIsActive(true)).thenReturn(List.of(sarah));
        String token = staffService.signIn("1111").token();

        // When
        staffService.signOut(token);

        // Then
        assertTrue(sessions.sessionFor(token).isEmpty());
    }

    // ------------------------------------------------------------------
    // Adding staff
    // ------------------------------------------------------------------

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
        String token = staffService.signIn("1111").token();
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
        String token = staffService.signIn("1111").token();
        when(staffRepository.findById(1L)).thenReturn(Optional.of(sarah));
        when(staffRepository.save(any(Staff.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        staffService.updateStaff(1L, new StaffUpdateRequest("Sara", null, null, null));

        // Then
        assertTrue(sessions.sessionFor(token).isPresent());
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
        assertEquals(4, StaffView.class.getRecordComponents().length);
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
}
