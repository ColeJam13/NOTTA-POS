package com.notapos.config;

import org.springframework.beans.factory.annotation.Value;
import java.time.Duration;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Decides which web pages are allowed to call this backend, and (in the
 * packaged build) how long a device may keep the frontend's files.
 *
 * WHAT: CORS (Cross-Origin Resource Sharing). A browser refuses to let a page
 *       loaded from one address (the React app on port 3000) read answers from
 *       another address (this backend on port 8080) unless the backend says
 *       that page's address - its "origin" - is allowed.
 * WHY:  The allowed origin used to be typed here as http://localhost:3000, so
 *       the app only worked in a browser on the same computer as the frontend.
 *       A tablet on the restaurant network opens the app at an address like
 *       http://192.168.1.50:3000, and every request from it was refused.
 *
 * HOW TO ALLOW ANOTHER ADDRESS:
 *   Set notta.cors.allowed-origins in application.properties (comma-separated),
 *   or set the NOTTA_CORS_ALLOWED_ORIGINS environment variable when starting
 *   the backend. With no setting, http://localhost:3000 is used.
 *
 * Spring Security uses these same rules (SecurityConfig asks for them with
 * cors(withDefaults())), and applies them before it looks at who is signed in.
 *
 * IN THE PACKAGED BUILD none of this CORS setting is needed: the frontend is
 * served by this backend, so the page and the API are the same origin, and a
 * browser asks no permission for that. The setting stays for development, where
 * the frontend runs on its own port.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    // The addresses the frontend may be served from. Spring splits the
    // comma-separated setting into an array for us.
    private final String[] allowedOrigins;

    public WebConfig(@Value("${notta.cors.allowed-origins:http://localhost:3000}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    /**
     * How long a device may keep its copy of the frontend's built files.
     *
     * In the packaged build the frontend's files are inside the backend's jar, in a
     * folder named static/, and Spring Boot serves anything in that folder by itself.
     * This method only adds one instruction for the files under /assets.
     *
     * WHY: the build names each file in /assets after its contents
     * (index-AxtGxFGR.js). Change one character of the code and the name changes.
     * So a file with a given name never changes, and a phone can keep it for a year
     * without asking again ("immutable"). Without this, every visit downloaded the
     * whole app again, including a 1.4 MB logo, over the restaurant's Wi-Fi.
     *
     * index.html is NOT covered, on purpose. Its name never changes and it names
     * the current scripts, so a device must fetch it fresh every time or it would
     * keep loading the old app after an update. Spring Security already marks
     * every answer "do not keep" unless something says otherwise, which is exactly
     * right for it.
     *
     * In development there is no static/ folder in the backend (the frontend runs
     * on its own port), and this does nothing.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable());
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*")
                // A browser shows a page only a short standard list of response headers
                // from another origin. Any other header the frontend needs to read has to
                // be named here: the backend's time, which every screen measures against.
                .exposedHeaders(ServerTimeFilter.SERVER_TIME_HEADER);
    }
}
