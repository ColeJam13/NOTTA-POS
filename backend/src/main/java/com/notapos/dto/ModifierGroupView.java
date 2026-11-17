package com.notapos.dto;

import java.util.List;

/**
 * One modifier group together with its choices, ready for the order screen's picker.
 * Example: "Bread Choice" (required, choose 1) with White Toast, Rye Toast, Biscuit...
 *
 * Unlike the raw ModifierGroup entity, this already contains the group's
 * modifiers, so the frontend does not need one more request per group.
 * Only active groups and active modifiers are ever put in here.
 *
 * @author CJ
 */
public record ModifierGroupView(
        Long modifierGroupId,
        String name,                    // e.g. "Bread Choice"
        String description,
        Boolean isRequired,             // true = the server must answer this group before the item can be added
        Integer maxSelections,          // The most choices allowed from this group
        List<ModifierOptionView> modifiers) {
}
