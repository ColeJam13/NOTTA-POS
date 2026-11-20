package com.notapos.security;

import com.notapos.enums.StaffRole;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Put this on a controller method to say who may call it.
 *
 * Example - only managers may see the Financials report:
 *
 *     @RequiresRole(StaffRole.MANAGER)
 *     @GetMapping
 *     public ResponseEntity<FinancialsReportView> getReport(...)
 *
 * StaffSessionInterceptor reads this before the method runs. A request with
 * no valid sign-in gets a 401; a signed-in person without one of the listed
 * roles gets a 403. A method without this annotation is open to everyone.
 *
 * @Retention(RUNTIME) keeps the annotation available while the app is running,
 * which is when the interceptor needs to read it.
 *
 * @author CJ
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequiresRole {

    StaffRole[] value();                // The roles that are allowed
}
