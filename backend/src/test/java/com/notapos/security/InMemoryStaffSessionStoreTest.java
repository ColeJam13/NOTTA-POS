package com.notapos.security;

import com.notapos.testsupport.InMemoryStaffSessionStore;
import com.notapos.testsupport.MutableClock;

/**
 * Runs the session-store tests (StaffSessionStoreContract) against the
 * in-memory stand-in that controller and service tests use.
 *
 * There are no tests written in this file: they are all in the contract, so
 * that the stand-in and the real store are held to the same behaviour.
 *
 * @author CJ
 */
class InMemoryStaffSessionStoreTest implements StaffSessionStoreContract {

    private final MutableClock clock = new MutableClock();
    private final StaffSessionStore sessions = new InMemoryStaffSessionStore(clock);

    @Override
    public StaffSessionStore sessions() {
        return sessions;
    }

    @Override
    public MutableClock clock() {
        return clock;
    }
}
