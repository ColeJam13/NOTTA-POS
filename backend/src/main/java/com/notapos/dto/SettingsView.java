package com.notapos.dto;

import java.math.BigDecimal;

/**
 * The restaurant's settings that a screen needs in order to show the same
 * numbers the backend will charge.
 *
 * It is its own answer (not a field tacked onto the menu or the sign-in) so
 * the next setting a screen needs has an obvious home. The template was the
 * first to move in beside the tax rate.
 *
 * @param taxRate  Sales tax as a decimal (0.03 = 3%). The same value OrderCheckService
 *                 works every check out with.
 * @param template The name of the template (the look) this restaurant uses, e.g.
 *                 "8-bit". The frontend holds the templates; see SettingsService.
 *
 * @author CJ
 */
public record SettingsView(BigDecimal taxRate, String template) {
}
