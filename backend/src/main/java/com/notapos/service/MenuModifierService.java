package com.notapos.service;

import com.notapos.dto.MenuItemModifiersView;
import com.notapos.dto.ModifierGroupView;
import com.notapos.dto.ModifierOptionView;
import com.notapos.entity.MenuItemModifierGroup;
import com.notapos.entity.Modifier;
import com.notapos.entity.ModifierGroup;
import com.notapos.repository.MenuItemModifierGroupRepository;
import com.notapos.repository.ModifierGroupRepository;
import com.notapos.repository.ModifierRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Answers "which modifier groups, with which choices, apply to a menu item?"
 *
 * WHAT: Builds the modifier picker's data: each group already contains its
 *       modifiers, and only active groups and active modifiers are included.
 * HOW:  Three database queries no matter how many items or groups exist:
 *         1. the links between menu items and groups
 *         2. the groups
 *         3. the modifiers in those groups
 *       The results are then matched up in memory using maps.
 * WHY:  The slow alternative is one request per group ("N+1"): a Combo Plate
 *       has three groups, so opening its picker would take four requests.
 *
 * The same rules are used twice: to fill the picker on the order screen, and
 * to check what the order screen sends back (see OrderItemModifierService).
 * That way the backend validates against exactly what the picker showed.
 *
 * @author CJ
 */
@Service
@Transactional(readOnly = true)      // All three queries see the same consistent snapshot of the data
public class MenuModifierService {

    private final MenuItemModifierGroupRepository menuItemModifierGroupRepository;
    private final ModifierGroupRepository modifierGroupRepository;
    private final ModifierRepository modifierRepository;

    @Autowired
    public MenuModifierService(MenuItemModifierGroupRepository menuItemModifierGroupRepository,
                               ModifierGroupRepository modifierGroupRepository,
                               ModifierRepository modifierRepository) {
        this.menuItemModifierGroupRepository = menuItemModifierGroupRepository;
        this.modifierGroupRepository = modifierGroupRepository;
        this.modifierRepository = modifierRepository;
    }

    public List<MenuItemModifiersView> getModifierGroupsForMenu() {             // Get the modifier groups for EVERY menu item that has any
        // Query 1: every link between a menu item and a modifier group
        List<MenuItemModifierGroup> links = menuItemModifierGroupRepository.findAll();
        if (links.isEmpty()) {
            return List.of();                                                   // No item has modifiers - skip the other two queries
        }

        // Queries 2 and 3: all active groups and all active modifiers, matched up by group ID
        Map<Long, ModifierGroupView> groupViewsById = buildGroupViews(
                modifierGroupRepository.findByIsActive(true),
                modifierRepository.findByIsActive(true));

        // Collect each menu item's groups. LinkedHashMap keeps the items in the order they were first seen.
        Map<Long, List<ModifierGroupView>> groupsByMenuItemId = new LinkedHashMap<>();
        for (MenuItemModifierGroup link : sortedByLinkId(links)) {
            ModifierGroupView group = groupViewsById.get(link.getModifierGroupId());
            if (group == null) {
                continue;                                                       // Group is inactive or has no active choices - leave it off the picker
            }
            List<ModifierGroupView> groupsForItem =
                    groupsByMenuItemId.computeIfAbsent(link.getMenuItemId(), id -> new ArrayList<>());
            if (!groupsForItem.contains(group)) {                               // Guard against the same group being linked to an item twice
                groupsForItem.add(group);
            }
        }

        return groupsByMenuItemId.entrySet().stream()
                .map(entry -> new MenuItemModifiersView(entry.getKey(), entry.getValue()))
                .toList();
    }

    public List<ModifierGroupView> getModifierGroupsForMenuItem(Long menuItemId) {     // Get the modifier groups for ONE menu item
        // Query 1: this item's links
        List<MenuItemModifierGroup> links = menuItemModifierGroupRepository.findByMenuItemId(menuItemId);
        if (links.isEmpty()) {
            return List.of();                                                   // Item has no modifiers - skip the other two queries
        }

        // Group IDs in the order they were linked (distinct = ignore a group linked twice)
        List<Long> groupIds = sortedByLinkId(links).stream()
                .map(MenuItemModifierGroup::getModifierGroupId)
                .distinct()
                .toList();

        // Queries 2 and 3: those groups (active only) and their active modifiers
        List<ModifierGroup> activeGroups = modifierGroupRepository.findAllById(groupIds).stream()
                .filter(group -> Boolean.TRUE.equals(group.getIsActive()))
                .toList();
        Map<Long, ModifierGroupView> groupViewsById = buildGroupViews(
                activeGroups,
                modifierRepository.findByModifierGroupIdInAndIsActive(groupIds, true));

        return groupIds.stream()
                .map(groupViewsById::get)
                .filter(Objects::nonNull)                                       // Drop inactive groups and groups with no active choices
                .toList();
    }

    // Links sorted by ID, so groups always appear on the picker in the order they were linked to the item
    private List<MenuItemModifierGroup> sortedByLinkId(List<MenuItemModifierGroup> links) {
        return links.stream()
                .sorted(Comparator.comparing(MenuItemModifierGroup::getMenuItemModifierGroupId))
                .toList();
    }

    // Put each group together with its modifiers. A group with no active modifiers is left out:
    // an empty group cannot be answered, and a REQUIRED empty group would block the item from being ordered.
    private Map<Long, ModifierGroupView> buildGroupViews(List<ModifierGroup> groups, List<Modifier> modifiers) {
        // Modifiers grouped by the group they belong to, sorted by ID so choices keep a steady order on screen
        Map<Long, List<ModifierOptionView>> optionsByGroupId = modifiers.stream()
                .sorted(Comparator.comparing(Modifier::getModifierId))
                .collect(Collectors.groupingBy(
                        Modifier::getModifierGroupId,
                        Collectors.mapping(this::toOptionView, Collectors.toList())));

        Map<Long, ModifierGroupView> groupViewsById = new LinkedHashMap<>();
        for (ModifierGroup group : groups) {
            List<ModifierOptionView> options = optionsByGroupId.get(group.getModifierGroupId());
            if (options == null) {
                continue;
            }
            groupViewsById.put(group.getModifierGroupId(), new ModifierGroupView(
                    group.getModifierGroupId(),
                    group.getName(),
                    group.getDescription(),
                    group.getIsRequired(),
                    group.getMaxSelections(),
                    options));
        }
        return groupViewsById;
    }

    private ModifierOptionView toOptionView(Modifier modifier) {
        return new ModifierOptionView(
                modifier.getModifierId(),
                modifier.getName(),
                // The price_adjustment column allows NULL - treat that as "no upcharge"
                modifier.getPriceAdjustment() != null ? modifier.getPriceAdjustment() : BigDecimal.ZERO);
    }
}
