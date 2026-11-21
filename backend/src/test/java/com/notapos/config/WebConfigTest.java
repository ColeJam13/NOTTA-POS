package com.notapos.config;

import com.notapos.controller.TableController;
import com.notapos.service.TableService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.notapos.testsupport.ControllerTest;
import com.notapos.testsupport.SignIn;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for WebConfig: which web pages may call the backend (CORS).
 *
 * WHAT: Sends requests that carry an "Origin" header, the way a browser does
 *       when a page on one address calls a backend on another.
 * WHY:  The allowed addresses come from the notta.cors.allowed-origins setting.
 *       If that stopped being read, a tablet on the restaurant network would
 *       be refused by the backend and show an empty app.
 *
 * @TestPropertySource sets the setting for this test only.
 *
 * @author CJ
 */

@ControllerTest(TableController.class)
@Import(WebConfig.class)
@TestPropertySource(properties = "notta.cors.allowed-origins=http://localhost:3000,http://192.168.1.50:3000")
class WebConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignIn signIn;              // Says who a request comes from: .with(signIn.asServer())

    @MockBean
    private TableService tableService;

    @Test
    void testRequestFromAnAllowedAddress_ShouldBeAnswered() throws Exception {
        // WHAT: Test a request from the second address in the setting
        // WHY: Every address in the comma-separated list must work, not only the first

        mockMvc.perform(get("/api/tables").with(signIn.asServer()).header("Origin", "http://192.168.1.50:3000"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://192.168.1.50:3000"));
    }

    @Test
    void testRequestFromAnAddressNotInTheSetting_ShouldBeRefused() throws Exception {
        // WHAT: Test a request from an address that is not allowed
        // WHY: Only the restaurant's own screens should be able to read orders and payments

        mockMvc.perform(get("/api/tables").with(signIn.asServer()).header("Origin", "http://somewhere-else.example"))
                .andExpect(status().isForbidden());
    }
}
