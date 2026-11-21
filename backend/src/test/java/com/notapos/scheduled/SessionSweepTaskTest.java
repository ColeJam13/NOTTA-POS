package com.notapos.scheduled;

import com.notapos.security.SignInGuard;
import com.notapos.security.StaffSessionStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.*;

/**
 * Tests for SessionSweepTask.
 *
 * Verifies that the background job asks both keepers to tidy up. What each
 * one removes, and what it must leave alone, is tested with the keeper itself
 * (StaffSessionStoreContract.testEndIdleSessions..., SignInGuardTest).
 *
 * @author CJ
 */
@ExtendWith(MockitoExtension.class)
class SessionSweepTaskTest {

    @Mock
    private StaffSessionStore sessions;

    @Mock
    private SignInGuard signInGuard;

    @InjectMocks
    private SessionSweepTask sessionSweepTask;

    @Test
    void testSweep_ShouldRemoveUnusedSessionsAndForgetQuietDevices() {
        // WHAT: Test that the scheduled task calls both keepers
        // WHY: A session whose token is never sent again is never looked up, so nothing
        //      else removes it; the same goes for a device that stops typing wrong PINs

        // Given - there is something to remove
        when(sessions.endIdleSessions()).thenReturn(3);
        when(signInGuard.forgetQuietDevices()).thenReturn(1);

        // When
        sessionSweepTask.sweep();

        // Then
        verify(sessions, times(1)).endIdleSessions();
        verify(signInGuard, times(1)).forgetQuietDevices();
    }

    @Test
    void testSweep_WhenThereIsNothingToRemove_ShouldStillRunBothAndNotFail() {
        // WHAT: Test the usual case: nothing to tidy
        // WHY: It runs every 15 minutes for as long as the backend is up

        // When
        sessionSweepTask.sweep();

        // Then
        verify(sessions).endIdleSessions();
        verify(signInGuard).forgetQuietDevices();
    }
}
