package com.notapos.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Makes session tokens, and the hash of one.
 *
 * A TOKEN is 32 random bytes written as text. It is handed to the device at
 * sign-in and is the only thing that says who a request comes from, so it must
 * be impossible to guess: SecureRandom produces numbers that cannot be predicted.
 *
 * THE HASH is what the backend keeps. SHA-256 turns any text into 64 hex
 * characters, always the same for the same text, and there is no way back from
 * the 64 characters to the text. To find a session the backend hashes the
 * token it was sent and looks that up.
 *
 * WHY NOT BCRYPT, which is what PINs are hashed with: BCrypt is slow on
 * purpose and mixes in a random "salt", both to protect secrets that are short
 * enough to guess (a 4-digit PIN has 10,000 possibilities). A token has more
 * possibilities than there are atoms in the universe, so it cannot be guessed
 * however fast the hash is. And a salted hash cannot be looked up: the same
 * token would hash differently every time.
 *
 * @author CJ
 */
public final class SessionTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    private SessionTokens() {
    }

    public static String newToken() {                               // A fresh token nobody could guess
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {                       // SHA-256 of a token, as 64 hex characters
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            // Every Java runtime is required to provide SHA-256
            throw new IllegalStateException("SHA-256 is not available", impossible);
        }
    }
}
