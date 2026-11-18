package com.notapos.service;

import com.notapos.dto.MenuItemModifiersView;
import com.notapos.dto.ModifierGroupView;
import com.notapos.entity.MenuItemModifierGroup;
import com.notapos.entity.Modifier;
import com.notapos.entity.ModifierGroup;
import com.notapos.repository.MenuItemModifierGroupRepository;
import com.notapos.repository.ModifierGroupRepository;
import com.notapos.repository.ModifierRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for MenuModifierService.
 *
 * WHAT: Tests that modifier groups are matched up with their choices and
 *       with the menu items they apply to
 * WHY: This is what the order screen's modifier picker shows, and what the
 *      backend checks a server's choices against - it has to be right
 *
 * @author CJ
 */
@ExtendWith(MockitoExtension.class)
class MenuModifierServiceTest {

    @Mock
    private MenuItemModifierGroupRepository menuItemModifierGroupRepository;

    @Mock
    private ModifierGroupRepository modifierGroupRepository;

    @Mock
    private ModifierRepository modifierRepository;

    @InjectMocks
    private MenuModifierService menuModifierService;

    // ---- small builders to keep the tests readable ----

    private MenuItemModifierGroup link(Long linkId, Long menuItemId, Long modifierGroupId) {
        MenuItemModifierGroup link = new MenuItemModifierGroup();
        link.setMenuItemModifierGroupId(linkId);
        link.setMenuItemId(menuItemId);
        link.setModifierGroupId(modifierGroupId);
        return link;
    }

    private ModifierGroup group(Long groupId, String name, boolean required, int maxSelections, boolean active) {
        ModifierGroup group = new ModifierGroup();
        group.setModifierGroupId(groupId);
        group.setName(name);
        group.setIsRequired(required);
        group.setMaxSelections(maxSelections);
        group.setIsActive(active);
        return group;
    }

    private Modifier modifier(Long modifierId, Long groupId, String name, String priceAdjustment) {
        Modifier modifier = new Modifier();
        modifier.setModifierId(modifierId);
        modifier.setModifierGroupId(groupId);
        modifier.setName(name);
        modifier.setPriceAdjustment(priceAdjustment != null ? new BigDecimal(priceAdjustment) : null);
        modifier.setIsActive(true);
        return modifier;
    }

    // ------------------------------------------------------------------------
    // getModifierGroupsForMenu - the whole menu at once
    // ------------------------------------------------------------------------

    @Test
    void testGetModifierGroupsForMenu_ShouldMatchGroupsAndChoicesToTheirMenuItems() {
        // Given - Combo Plate (item 1) has Bread + Side; Fries (item 26) has Size.
        //         Links and modifiers arrive from the database out of order.
        when(menuItemModifierGroupRepository.findAll()).thenReturn(Arrays.asList(
                link(11L, 26L, 7L),
                link(3L, 1L, 3L),
                link(2L, 1L, 2L)));
        when(modifierGroupRepository.findByIsActive(true)).thenReturn(Arrays.asList(
                group(2L, "Bread Choice", true, 1, true),
                group(3L, "Side Choice", true, 1, true),
                group(7L, "Fries Size", true, 1, true)));
        when(modifierRepository.findByIsActive(true)).thenReturn(Arrays.asList(
                modifier(14L, 2L, "French Toast", "2.00"),
                modifier(8L, 2L, "White Toast", "0.00"),
                modifier(16L, 3L, "Hashbrowns", "0.00"),
                modifier(29L, 7L, "Half", "0.00"),
                modifier(30L, 7L, "Full", "4.00")));

        // When
        List<MenuItemModifiersView> result = menuModifierService.getModifierGroupsForMenu();

        // Then - one entry per menu item that has modifiers
        assertEquals(2, result.size());
        MenuItemModifiersView comboPlate = result.stream().filter(v -> v.menuItemId().equals(1L)).findFirst().orElseThrow();
        MenuItemModifiersView fries = result.stream().filter(v -> v.menuItemId().equals(26L)).findFirst().orElseThrow();

        // Groups come back in the order they were linked (link 2 before link 3)
        assertEquals(2, comboPlate.groups().size());
        ModifierGroupView bread = comboPlate.groups().get(0);
        assertEquals("Bread Choice", bread.name());
        assertTrue(bread.isRequired());
        assertEquals(1, bread.maxSelections());
        assertEquals("Side Choice", comboPlate.groups().get(1).name());

        // Choices are sorted by ID and carry their upcharge
        assertEquals(2, bread.modifiers().size());
        assertEquals("White Toast", bread.modifiers().get(0).name());
        assertEquals("French Toast", bread.modifiers().get(1).name());
        assertEquals(new BigDecimal("2.00"), bread.modifiers().get(1).priceAdjustment());

        assertEquals(1, fries.groups().size());
        assertEquals(2, fries.groups().get(0).modifiers().size());

        // The whole point: three queries total, never one per group
        verify(menuItemModifierGroupRepository, times(1)).findAll();
        verify(modifierGroupRepository, times(1)).findByIsActive(true);
        verify(modifierRepository, times(1)).findByIsActive(true);
        verify(modifierRepository, never()).findByModifierGroupId(any());
    }

    @Test
    void testGetModifierGroupsForMenu_WhenNoLinks_ShouldSkipOtherQueries() {
        // Given - no menu item has modifiers
        when(menuItemModifierGroupRepository.findAll()).thenReturn(List.of());

        // When
        List<MenuItemModifiersView> result = menuModifierService.getModifierGroupsForMenu();

        // Then - empty list and no wasted queries
        assertTrue(result.isEmpty());
        verifyNoInteractions(modifierGroupRepository, modifierRepository);
    }

    @Test
    void testGetModifierGroupsForMenu_ShouldLeaveOutInactiveAndEmptyGroups() {
        // Given - item 1 is linked to three groups:
        //         group 2 is fine, group 5 is inactive (so findByIsActive does not return it),
        //         and group 6 is active but has no active modifiers
        when(menuItemModifierGroupRepository.findAll()).thenReturn(Arrays.asList(
                link(1L, 1L, 2L),
                link(2L, 1L, 5L),
                link(3L, 1L, 6L),
                link(4L, 9L, 5L)));
        when(modifierGroupRepository.findByIsActive(true)).thenReturn(Arrays.asList(
                group(2L, "Bread Choice", true, 1, true),
                group(6L, "Wrap Additions", true, 5, true)));
        when(modifierRepository.findByIsActive(true)).thenReturn(Arrays.asList(
                modifier(8L, 2L, "White Toast", "0.00")));

        // When
        List<MenuItemModifiersView> result = menuModifierService.getModifierGroupsForMenu();

        // Then - only the usable group is left, and item 9 (whose only group is inactive) is not listed at all
        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).menuItemId());
        assertEquals(1, result.get(0).groups().size());
        assertEquals("Bread Choice", result.get(0).groups().get(0).name());
    }

    @Test
    void testGetModifierGroupsForMenu_WhenPriceAdjustmentIsNull_ShouldTreatItAsZero() {
        // Given - the price_adjustment column allows NULL
        when(menuItemModifierGroupRepository.findAll()).thenReturn(Arrays.asList(link(1L, 1L, 2L)));
        when(modifierGroupRepository.findByIsActive(true)).thenReturn(Arrays.asList(
                group(2L, "Bread Choice", true, 1, true)));
        when(modifierRepository.findByIsActive(true)).thenReturn(Arrays.asList(
                modifier(8L, 2L, "White Toast", null)));

        // When
        List<MenuItemModifiersView> result = menuModifierService.getModifierGroupsForMenu();

        // Then - no upcharge instead of a null the price math would trip over
        assertEquals(BigDecimal.ZERO, result.get(0).groups().get(0).modifiers().get(0).priceAdjustment());
    }

    // ------------------------------------------------------------------------
    // getModifierGroupsForMenuItem - one menu item
    // ------------------------------------------------------------------------

    @Test
    void testGetModifierGroupsForMenuItem_ShouldReturnItsGroupsInLinkOrder() {
        // Given - Combo Plate has Side (link 3) and Bread (link 2)
        when(menuItemModifierGroupRepository.findByMenuItemId(1L)).thenReturn(Arrays.asList(
                link(3L, 1L, 3L),
                link(2L, 1L, 2L)));
        when(modifierGroupRepository.findAllById(Arrays.asList(2L, 3L))).thenReturn(Arrays.asList(
                group(3L, "Side Choice", true, 1, true),
                group(2L, "Bread Choice", true, 1, true)));
        when(modifierRepository.findByModifierGroupIdInAndIsActive(anyCollection(), eq(true))).thenReturn(Arrays.asList(
                modifier(16L, 3L, "Hashbrowns", "0.00"),
                modifier(8L, 2L, "White Toast", "0.00"),
                modifier(14L, 2L, "French Toast", "2.00")));

        // When
        List<ModifierGroupView> result = menuModifierService.getModifierGroupsForMenuItem(1L);

        // Then - Bread first (it was linked first), each group with its own choices
        assertEquals(2, result.size());
        assertEquals("Bread Choice", result.get(0).name());
        assertEquals(2, result.get(0).modifiers().size());
        assertEquals("Side Choice", result.get(1).name());
        assertEquals(1, result.get(1).modifiers().size());

        // One modifiers query for all of the item's groups
        verify(modifierRepository, times(1)).findByModifierGroupIdInAndIsActive(anyCollection(), eq(true));
    }

    @Test
    void testGetModifierGroupsForMenuItem_WhenItemHasNoGroups_ShouldSkipOtherQueries() {
        // Given - a plain item such as a soda
        when(menuItemModifierGroupRepository.findByMenuItemId(40L)).thenReturn(List.of());

        // When
        List<ModifierGroupView> result = menuModifierService.getModifierGroupsForMenuItem(40L);

        // Then
        assertTrue(result.isEmpty());
        verifyNoInteractions(modifierGroupRepository, modifierRepository);
    }

    @Test
    void testGetModifierGroupsForMenuItem_ShouldLeaveOutInactiveGroups() {
        // Given - the item's second group has been switched off
        when(menuItemModifierGroupRepository.findByMenuItemId(1L)).thenReturn(Arrays.asList(
                link(2L, 1L, 2L),
                link(3L, 1L, 3L)));
        when(modifierGroupRepository.findAllById(any())).thenReturn(Arrays.asList(
                group(2L, "Bread Choice", true, 1, true),
                group(3L, "Side Choice", true, 1, false)));
        when(modifierRepository.findByModifierGroupIdInAndIsActive(anyCollection(), eq(true))).thenReturn(Arrays.asList(
                modifier(8L, 2L, "White Toast", "0.00"),
                modifier(16L, 3L, "Hashbrowns", "0.00")));

        // When
        List<ModifierGroupView> result = menuModifierService.getModifierGroupsForMenuItem(1L);

        // Then - the inactive group is not offered (and so can never be "required")
        assertEquals(1, result.size());
        assertEquals("Bread Choice", result.get(0).name());
    }
}
