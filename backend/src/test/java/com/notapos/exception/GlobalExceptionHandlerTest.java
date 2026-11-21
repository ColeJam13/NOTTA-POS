package com.notapos.exception;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for the error answers that come from the database layer, not from a service.
 *
 * WHAT: A tiny controller that does nothing but throw, wired to the real
 *       GlobalExceptionHandler, so each test can check the status and message a
 *       screen would receive.
 * WHY:  These two exceptions are thrown by Hibernate and PostgreSQL when two
 *       terminals collide. They are hard to cause on demand through the real
 *       endpoints (the row locks stop most collisions earlier), but the answer
 *       still has to be a plain 409 and not "Something went wrong on the server".
 *
 * standaloneSetup builds MockMvc around just these two objects. No Spring
 * context and no database start, so these run in milliseconds.
 *
 * @author CJ
 */
class GlobalExceptionHandlerTest {

    // A controller whose only job is to throw what the tests need
    @RestController
    static class ThrowingController {

        @GetMapping("/test/changed-elsewhere")          // What Hibernate throws when an order's version does not match
        String changedElsewhere() {
            throw new ObjectOptimisticLockingFailureException("com.notapos.entity.Order", 1L);
        }

        @GetMapping("/test/second-open-order")          // What PostgreSQL's refusal of a second open order arrives as
        String secondOpenOrder() {
            throw new DataIntegrityViolationException("could not execute statement",
                    new RuntimeException("ERROR: duplicate key value violates unique constraint \"orders_one_open_per_table\""));
        }

        @GetMapping("/test/other-database-rule")        // Some other broken rule: a defect, not the user's doing
        String otherDatabaseRule() {
            throw new DataIntegrityViolationException("could not execute statement",
                    new RuntimeException("ERROR: null value in column \"table_id\" violates not-null constraint"));
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void testOrderChangedOnAnotherTerminal_ShouldBeA409ThatSaysSo() throws Exception {
        // WHAT: Test the answer when a save started from an out-of-date copy of an order
        // WHY: Without a handler this was a 500 "Something went wrong on the server", which
        //      tells the server at the screen nothing. It is not a fault: someone else got
        //      there first, and trying again from the current check is the right next step.

        mockMvc.perform(get("/test/changed-elsewhere"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This check was just changed on another terminal. Reopen it and try again."));
    }

    @Test
    void testSecondOpenOrderRefusedByTheDatabase_ShouldBeA409ThatSaysSo() throws Exception {
        // WHAT: Test the answer when the database's one-open-order-per-table rule refuses a row
        // WHY: The rule is the last line of defense, so reaching it means some path got past
        //      the service code. The person at the screen should still get words they can act on.

        mockMvc.perform(get("/test/second-open-order"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("This table already has an open check. Reopen the table to see it."));
    }

    @Test
    void testAnyOtherDatabaseRule_ShouldStayA500WithNoDetails() throws Exception {
        // WHAT: Test that other broken database rules are still reported as a server error
        // WHY: They are defects in the code. The table and column names in the database's
        //      message are for the log, not for the screen.

        mockMvc.perform(get("/test/other-database-rule"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("Something went wrong on the server"));
    }
}
