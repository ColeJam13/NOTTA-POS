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
 * @Valid on the list below) and a bad one becomes a normal 400 response
 * through GlobalExceptionHandler, the same as every other endpoint.
 *
 * Example:
 * { "tables": [ { "tableId": 1, "x": 120, "y": 40, "rotation": 0 } ] }
 *
 * @param tables One entry per table that moved
 */
public record TableLayoutRequest(
        @NotEmpty(message = "At least one table is required")
        @Valid
        List<TablePositionRequest> tables) {
}
