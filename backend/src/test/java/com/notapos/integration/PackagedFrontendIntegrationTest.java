package com.notapos.integration;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for the backend serving the frontend's own files.
 *
 * THE PACKAGED BUILD: for a restaurant (or the home test host) the app is one
 * file. The built frontend is put inside the backend's jar, under static/, and
 * Spring Boot serves it. A phone opens http://192.168.1.50:8080 and gets the
 * sign-in screen from the same address the API is on.
 *
 * THE TENSION: every request to this backend needs a signed-in person
 * ("default deny"). But the sign-in screen is itself a file the backend has
 * to hand over, to someone who by definition is not signed in yet.
 *
 * THE RULE THESE TESTS PIN DOWN: a short, exact list of the frontend's files
 * can be read (GET) without a sign-in. Nothing else changed: the API, any
 * other address, and any other kind of request are refused as before.
 *
 * The files served here are small stand-ins in src/test/resources/static.
 *
 * @author CJ
 */
class PackagedFrontendIntegrationTest extends BaseIntegrationTest {

    // ------------------------------------------------------------------
    // The frontend's files are handed to anyone
    // ------------------------------------------------------------------

    @Test
    void testTheSignInPage_ShouldBeServedToSomeoneWhoIsNotSignedIn() throws Exception {
        // WHAT: GET /index.html with no token
        // WHY: This is the page that shows the PIN pad. If it needed a sign-in, nobody
        //      could ever sign in.

        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("<div id=\"root\">")));
    }

    @Test
    void testTheBareAddress_ShouldLeadToTheSignInPage() throws Exception {
        // WHAT: GET / with no token, which is what typing the address into a phone sends
        // WHY: Spring Boot answers "/" by passing the request on to /index.html inside the
        //      server (a "forward"). Both steps have to be allowed.

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    void testTheBuiltScriptsAndStyles_ShouldBeServedToSomeoneWhoIsNotSignedIn() throws Exception {
        // WHAT: GET a file under /assets with no token
        // WHY: The sign-in page is nearly empty by itself. The screens are in the
        //      JavaScript and CSS files it names, all of which the build puts in /assets.

        mockMvc.perform(get("/assets/app-test.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("test stand-in")));
    }

    @Test
    void testAFrontendFileThatIsNotThere_ShouldBeNotFoundRatherThanSignInFirst() throws Exception {
        // WHAT: GET /favicon.ico, which is on the list but has no stand-in file in the tests
        // WHY: The answer for a missing public file is 404. A 401 here would mean the list
        //      of public files is not being applied.

        mockMvc.perform(get("/favicon.ico"))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------
    // How long a device may keep its copy
    // ------------------------------------------------------------------

    @Test
    void testBuiltAssets_ShouldBeKeptByTheDeviceForAYear() throws Exception {
        // WHAT: The Cache-Control header on a file under /assets
        // WHY: The build names each of these files after its contents (index-AxtGxFGR.js),
        //      so a file with a given name never changes. A phone can keep it and not
        //      download it again on every visit. "immutable" tells the browser not to even ask.

        mockMvc.perform(get("/assets/app-test.js"))
                .andExpect(header().string("Cache-Control", containsString("max-age=31536000")))
                .andExpect(header().string("Cache-Control", containsString("immutable")));
    }

    @Test
    void testTheSignInPage_ShouldNeverBeKeptByTheDevice() throws Exception {
        // WHAT: The Cache-Control header on index.html
        // WHY: index.html is the one file whose name does not change, and it names the
        //      current scripts. A device that kept an old copy would keep loading the old
        //      app after an update.

        mockMvc.perform(get("/index.html"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Cache-Control", not(containsString("immutable"))));
    }

    // ------------------------------------------------------------------
    // Nothing else opened up
    // ------------------------------------------------------------------

    @Test
    void testTheApi_ShouldStillNeedASignIn() throws Exception {
        // WHAT: The same API requests as before, with no token
        // WHY: Serving the frontend's files must not have loosened the rule for anything else

        mockMvc.perform(get("/api/tables")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/menu-items")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/settings")).andExpect(status().isUnauthorized());
    }

    @Test
    void testAnAddressThatIsNotOnTheList_ShouldStillNeedASignIn() throws Exception {
        // WHAT: Addresses outside /api that are not the frontend's files
        // WHY: The rule is a list of what is public, not "everything outside /api is
        //      public". Anything added to the backend later, at any address, starts
        //      closed, as it does today.

        mockMvc.perform(get("/anything-else")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/static/index.html")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/application.properties")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/error")).andExpect(status().isUnauthorized());
    }

    @Test
    void testOnlyReading_ShouldBeAllowedOnTheFrontendsFiles() throws Exception {
        // WHAT: POST, PUT, and DELETE to the public addresses, with no token
        // WHY: Handing a file over is all that is public. Any other kind of request to the
        //      same address is refused before anything looks at it.

        mockMvc.perform(post("/")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/index.html")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/assets/app-test.js")).andExpect(status().isUnauthorized());
    }

    @Test
    void testASignedInPerson_ShouldGetTheFilesTheSameWay() throws Exception {
        // WHAT: The same page asked for by someone holding a token (a reload mid-shift)
        // WHY: The public files are public for everyone, signed in or not, including a
        //      manager who has not yet changed the starter PIN: the page that asks for
        //      the new PIN is one of these files.

        mockMvc.perform(get("/index.html").with(signIn.asServer())).andExpect(status().isOk());
        mockMvc.perform(get("/index.html").with(signIn.asManagerWhoMustChangePin())).andExpect(status().isOk());
        mockMvc.perform(get("/assets/app-test.js").with(signIn.asManagerWhoMustChangePin())).andExpect(status().isOk());
    }
}
