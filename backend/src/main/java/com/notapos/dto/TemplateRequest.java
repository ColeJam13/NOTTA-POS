package com.notapos.dto;

import com.notapos.service.SettingsService;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The body of PUT /api/settings/template: the template a manager is choosing.
 *
 * The name must be a plain short word made of lower-case letters, digits, and
 * hyphens ("8-bit", "coffee-shop"). SettingsService explains why.
 *
 * @param template The template's name
 */
public record TemplateRequest(
        @NotNull(message = "Say which template")
        @Size(max = SettingsService.TEMPLATE_NAME_MAX_LENGTH, message = "That is not a template's name")
        @Pattern(regexp = SettingsService.TEMPLATE_NAME_PATTERN, message = "That is not a template's name")
        String template) {
}
