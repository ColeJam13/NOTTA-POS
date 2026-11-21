package com.notapos.integration;

import com.notapos.enums.StaffRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the restaurant's chosen template (step B4b of the
 * Phase 2 plan).
 *
 * WHAT A TEMPLATE IS: the look of the whole app (colors, fonts, shapes). The
 * frontend holds the templates themselves; the backend holds only WHICH one
 * this restaurant uses, as a short name such as "8-bit".
 *
 * WHY THE BACKEND HOLDS IT: a choice kept in one browser would give every
 * terminal and phone its own look. Kept here, a manager chooses once and every
 * device shows the same thing.
 *
 * THE RULES:
 *   - Anyone signed in can read it (GET /api/settings, beside the tax rate).
 *   - Only a manager can change it (PUT /api/settings/template).
 *   - With nothing chosen yet, it is "8-bit", the look the app has always had.
 *   - The name is checked to be a plain short word. The frontend puts it into
 *     the page, so it must never be able to carry anything but a name.
 *
 * The first version of these tests was written before the feature and failed.
 *
 * @author CJ
 */
class TemplateSettingIntegrationTest extends BaseIntegrationTest {

    @Autowired private JdbcTemplate jdbc;

    private RequestPostProcessor morgan;        // A manager
    private RequestPostProcessor sarah;         // A server

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM settings");    // Nothing chosen yet (nothing rolls back by itself here)
        morgan = signIn.as(2L, "Morgan", StaffRole.MANAGER);
        sarah = signIn.as(7L, "Sarah", StaffRole.SERVER);
    }

    private static MockHttpServletRequestBuilder choose(String template) {
        return put("/api/settings/template")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"template\":\"" + template + "\"}");
    }

    private String templateAsReadBy(RequestPostProcessor who) throws Exception {
        String body = mockMvc.perform(get("/api/settings").with(who))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.template");
    }

    // ------------------------------------------------------------------
    // Reading it
    // ------------------------------------------------------------------

    @Test
    void testWithNothingChosen_ShouldAnswerTheLookTheAppHasAlwaysHad() throws Exception {
        // WHAT: Read the settings on a database where no template was ever chosen
        // WHY: Every existing restaurant is in this position on the day this ships.
        //      Its screens must not change: the owner's decision (D9) is that 8-Bit,
        //      today's look, is the default.

        mockMvc.perform(get("/api/settings").with(sarah))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template").value("8-bit"))
                .andExpect(jsonPath("$.taxRate").value(0.03));      // The tax rate is still in the same answer
    }

    @Test
    void testEveryRole_ShouldBeAbleToReadTheTemplate() throws Exception {
        // WHAT: Read it as each role
        // WHY: Every terminal has to draw itself, whoever is signed in at it

        for (StaffRole role : StaffRole.values()) {
            assertEquals("8-bit", templateAsReadBy(signIn.as(role)), "as " + role);
        }
    }

    // ------------------------------------------------------------------
    // Changing it
    // ------------------------------------------------------------------

    @Test
    void testAManagerChoosingATemplate_ShouldChangeItForEveryone() throws Exception {
        // WHAT: The manager chooses "plain"; a server on another terminal reads the settings
        // WHY: The point of keeping the choice on the backend

        mockMvc.perform(choose("plain").with(morgan))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.template").value("plain"))      // The answer is the settings as they now stand
                .andExpect(jsonPath("$.taxRate").value(0.03));

        assertEquals("plain", templateAsReadBy(sarah));
        assertEquals("plain", templateAsReadBy(signIn.asKitchen()));
    }

    @Test
    void testChoosingATemplate_ShouldRecordWhoChoseItAndWhen() throws Exception {
        // WHAT: Look at the stored row after a change
        // WHY: A whole restaurant's screens changing at once is the kind of thing a
        //      manager will ask "who did that?" about

        mockMvc.perform(choose("plain").with(morgan)).andExpect(status().isOk());

        Map<String, Object> row = jdbc.queryForMap("SELECT * FROM settings WHERE setting_key = 'template'");
        assertEquals("plain", row.get("setting_value"));
        assertEquals(2L, ((Number) row.get("updated_by_staff_id")).longValue());
        assertEquals("Morgan", row.get("updated_by_name"));
        assertNotNull(row.get("updated_at"));
    }

    @Test
    void testChoosingAgain_ShouldReplaceTheChoiceNotAddASecondOne() throws Exception {
        // WHAT: Choose twice, then count the rows
        // WHY: There is one template for the restaurant. Two rows would leave "which
        //      one is it?" to chance.

        mockMvc.perform(choose("plain").with(morgan)).andExpect(status().isOk());
        mockMvc.perform(choose("8-bit").with(morgan)).andExpect(status().isOk());

        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM settings WHERE setting_key = 'template'", Integer.class));
        assertEquals("8-bit", templateAsReadBy(sarah));
    }

    @Test
    void testAnyoneButAManager_ShouldNotBeAbleToChangeIt() throws Exception {
        // WHAT: Each other role tries to choose a template, and so does nobody
        // WHY: One person's taste must not restyle the terminals of a whole floor
        //      mid-shift. It is the manager's decision.

        for (StaffRole role : List.of(StaffRole.SERVER, StaffRole.BARTENDER, StaffRole.HOST, StaffRole.KITCHEN)) {
            mockMvc.perform(choose("plain").with(signIn.as(role)))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(choose("plain"))
                .andExpect(status().isUnauthorized());

        assertEquals("8-bit", templateAsReadBy(sarah));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM settings", Integer.class));
    }

    @Test
    void testANameThatIsNotAPlainShortWord_ShouldBeRefused() throws Exception {
        // WHAT: Try to store things that are not template names
        // WHY: The frontend writes this value into the page (as an attribute that picks
        //      the stylesheet). Only lower-case letters, digits, and hyphens are ever
        //      needed for a name, so nothing else is stored: no spaces, no quotes, no
        //      angle brackets, nothing long.

        for (String notAName : List.of("", " ", "Plain", "coffee shop", "plain;", "<script>", "a/b", "x\\\"y",
                "this-name-is-far-too-long-to-be-the-name-of-a-template")) {
            mockMvc.perform(choose(notAName).with(morgan))
                    .andExpect(status().isBadRequest());
        }
        mockMvc.perform(put("/api/settings/template").with(morgan)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());

        assertEquals("8-bit", templateAsReadBy(sarah));
    }
}
