package com.notapos.config;

import com.notapos.security.StaffSessionInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Decides which web pages are allowed to call this backend.
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
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    // The addresses the frontend may be served from. Spring splits the
    // comma-separated setting into an array for us.
    private final String[] allowedOrigins;

    // Checks who is signed in before each request reaches a controller
    private final StaffSessionInterceptor staffSessionInterceptor;

    public WebConfig(@Value("${notta.cors.allowed-origins:http://localhost:3000}") String[] allowedOrigins,
                     StaffSessionInterceptor staffSessionInterceptor) {
        this.allowedOrigins = allowedOrigins;
        this.staffSessionInterceptor = staffSessionInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(staffSessionInterceptor).addPathPatterns("/api/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*");
    }
}
