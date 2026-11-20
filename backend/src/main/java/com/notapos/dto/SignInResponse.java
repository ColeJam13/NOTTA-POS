package com.notapos.dto;

/**
 * The answer to a successful sign-in.
 *
 * @param staff Who signed in
 * @param token A long random text the frontend sends back with every request
 *              (in the X-Staff-Token header) to show who is asking
 */
public record SignInResponse(StaffView staff, String token) {
}
