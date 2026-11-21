package com.notapos.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * The body of PUT /api/tables/layout: the tables that moved, each with its
 * new position and rotation.
 *
 * WHY a wrapper around the list: @Valid on a request body checks an object's
 * fields. With the list inside an object, Spring checks every entry (the
 * @Valid inside the list's angle brackets below) and a bad one becomes a
 * normal 400 response through GlobalExceptionHandler, the same as every other
 * endpoint.
 *
 * WHERE THE @Valid SITS: "List<@Valid TablePositionRequest>" reads "a list
 * whose every entry is checked". It used to be written in front of the whole
 * list (@Valid List<...>), which did the same thing; the validation library
 * that came with Spring Boot 4 calls that form out of date and logs a warning
 * for it at start-up.
 *
 * Example:
 * { "tables": [ { "tableId": 1, "x": 120, "y": 40, "rotation": 0 } ] }
 *
 * @param tables One entry per table that moved
 */
public record TableLayoutRequest(
        @NotEmpty(message = "At least one table is required")
        List<@Valid TablePositionRequest> tables) {
}
