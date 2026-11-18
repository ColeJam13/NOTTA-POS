package com.notapos.dto;

import java.util.List;

/**
 * Every modifier group that applies to one menu item.
 *
 * The order screen downloads a list of these once (one entry per menu item
 * that has modifiers) and then knows, without asking the backend again,
 * whether tapping an item should open the modifier picker.
 *
 * @author CJ
 */
public record MenuItemModifiersView(
        Long menuItemId,
        List<ModifierGroupView> groups) {       // In the order the groups were linked to the item
}
