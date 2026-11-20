package com.notapos.config;

import com.notapos.service.StaffService;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Runs once each time the backend starts, after the database tables exist.
 *
 * If there are no staff at all, nobody can sign in - and so nobody could add
 * the first staff member. This asks StaffService to create a first manager
 * in that case (see StaffService.ensureFirstManager). When staff already
 * exist it does nothing.
 *
 * An ApplicationRunner is Spring Boot's hook for "do this once at start-up".
 *
 * @author CJ
 */
@Component
public class FirstManagerSetup implements ApplicationRunner {

    private final StaffService staffService;

    public FirstManagerSetup(StaffService staffService) {
        this.staffService = staffService;
    }

    @Override
    public void run(ApplicationArguments args) {
        staffService.ensureFirstManager();
    }
}
