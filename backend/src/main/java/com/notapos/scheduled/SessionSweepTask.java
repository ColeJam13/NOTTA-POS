package com.notapos.scheduled;

import com.notapos.security.SignInGuard;
import com.notapos.security.StaffSessionStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background job that clears out what sign-in no longer needs.
 *
 * Two things pile up if nobody tidies them:
 *
 *   1. Sessions that were never signed out of. A tablet that went home in a
 *      bag, a browser tab that was closed. Each is a row in staff_sessions.
 *      A session unused for 12 hours is already refused when its token is next
 *      sent; this removes the ones whose token is never sent again.
 *
 *   2. What SignInGuard remembers about devices that typed wrong PINs. A
 *      device with a clean day behind it is forgotten.
 *
 * Runs every 15 minutes, starting one minute after the backend does. Nothing
 * depends on it running on time: it only removes what is already worthless.
 *
 * @author CJ
 */
@Component
public class SessionSweepTask {

    private static final Logger log = LoggerFactory.getLogger(SessionSweepTask.class);

    private final StaffSessionStore sessions;
    private final SignInGuard signInGuard;

    public SessionSweepTask(StaffSessionStore sessions, SignInGuard signInGuard) {
        this.sessions = sessions;
        this.signInGuard = signInGuard;
    }

    @Scheduled(fixedRate = 15 * 60 * 1000, initialDelay = 60 * 1000)       // Milliseconds: every 15 minutes, first run after 1 minute
    public void sweep() {
        int sessionsEnded = sessions.endIdleSessions();
        int devicesForgotten = signInGuard.forgetQuietDevices();

        if (sessionsEnded > 0 || devicesForgotten > 0) {
            log.info("Sign-in sweep: {} unused session(s) removed, {} device(s) given a clean slate",
                    sessionsEnded, devicesForgotten);
        }
    }
}
