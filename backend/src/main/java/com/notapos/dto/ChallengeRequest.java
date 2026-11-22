package com.notapos.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * What a manager fills in to make a challenge, or to change one.
 *
 * The annotations check one field at a time (a name is there, a target is above
 * zero). The rules that involve two fields are in ChallengeService: a challenge
 * that counts items needs a category or an item, the end must be after the start.
 *
 * Who made it is NOT in here: that is the signed-in manager, taken from the
 * request's sign-in, never from what the request says.
 *
 * @param name         What it is called, e.g. "Dessert Champion"
 * @param description  A line about it (optional)
 * @param measure      "items_sold", "total_sales", "check_average", or "biggest_check" (ChallengeMeasure)
 * @param target       What a person has to reach: a number of items, or dollars
 * @param menuCategory items_sold only: the category whose items count...
 * @param menuItemId   ...or the one menu item that counts. Exactly one of the two.
 * @param minChecks    check_average only: how many checks before an average counts
 * @param startsAt     When it starts, on the restaurant's own clock with no zone
 *                     (2026-11-22T17:00), the way the reports' periods are written
 * @param endsAt       When it ends, written the same way
 * @param prize        Words a manager writes, e.g. "$20" (optional)
 */
public record ChallengeRequest(
        @NotBlank(message = "A challenge needs a name")
        @Size(max = 60, message = "The name can be 60 characters at most")
        String name,

        @Size(max = 300, message = "The description can be 300 characters at most")
        String description,

        @NotBlank(message = "Pick what the challenge measures")
        String measure,

        @NotNull(message = "A challenge needs a target")
        @DecimalMin(value = "0.01", message = "The target has to be more than zero")
        @Digits(integer = 8, fraction = 2, message = "The target can have 8 digits and 2 decimals at most")
        BigDecimal target,

        @Size(max = 50, message = "A category can be 50 characters at most")
        String menuCategory,

        Long menuItemId,

        @Min(value = 1, message = "The number of checks has to be at least 1")
        @Max(value = 1000, message = "The number of checks can be 1000 at most")
        Integer minChecks,

        @NotNull(message = "A challenge needs a start")
        LocalDateTime startsAt,

        @NotNull(message = "A challenge needs an end")
        LocalDateTime endsAt,

        @Size(max = 100, message = "The prize can be 100 characters at most")
        String prize) {
}
