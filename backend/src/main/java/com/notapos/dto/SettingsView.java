package com.notapos.dto;

import java.math.BigDecimal;

/**
 * The restaurant's settings that a screen needs in order to show the same
 * numbers the backend will charge.
 *
 * Today that is one number. It is its own answer (not a field tacked onto the
 * menu or the sign-in) so the next setting a screen needs has an obvious home.
 *
 * @param taxRate Sales tax as a decimal (0.03 = 3%). The same value OrderCheckService
 *                works every check out with.
 *
 * @author CJ
 */
public record SettingsView(BigDecimal taxRate) {
}
