package com.notapos.service;

import com.notapos.dto.ModifierGroupView;
import com.notapos.dto.ModifierOptionView;
import com.notapos.dto.OrderItemModifierView;
import com.notapos.dto.OrderItemModifiersRequest;
import com.notapos.dto.OrderItemRequest;
import com.notapos.entity.MenuItem;
import com.notapos.entity.Modifier;
import com.notapos.entity.OrderItem;
import com.notapos.entity.OrderItemModifier;
import com.notapos.exception.InvalidModifierSelectionException;
import com.notapos.exception.ItemLockedException;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.repository.MenuItemRepository;
import com.notapos.repository.ModifierRepository;
import com.notapos.repository.OrderItemModifierRepository;
import com.notapos.repository.OrderItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderItemModifierService.
 * 
 * Tests tracking which modifiers were selected for order items.
 * 
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class OrderItemModifierServiceTest {

    @Mock
    private OrderItemModifierRepository orderItemModifierRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private MenuItemRepository menuItemRepository;

    @Mock
    private ModifierRepository modifierRepository;

    @Mock
    private MenuModifierService menuModifierService;

    @Mock
    private OrderItemService orderItemService;

    @InjectMocks
    private OrderItemModifierService orderItemModifierService;

    private OrderItemModifier testOrderItemModifier;

    @BeforeEach
    void setUp() {
        // Create a test order item modifier (Chicken Cutty with Fries)
        testOrderItemModifier = new OrderItemModifier();
        testOrderItemModifier.setOrderItemModifierId(1L);
        testOrderItemModifier.setOrderItemId(1L);
        testOrderItemModifier.setModifierId(1L);
        testOrderItemModifier.setPriceAdjustment(BigDecimal.ZERO);
    }

    @Test
    void testCreateOrderItemModifier_ShouldSaveModifier() {
        // WHAT: Test creating a new order item modifier
        // WHY: Track which modifiers customer selected (Fries, Add Bacon, etc.)
        
        // Given - Mock returns saved modifier
        when(orderItemModifierRepository.save(any(OrderItemModifier.class))).thenReturn(testOrderItemModifier);

        // When - Create order item modifier
        OrderItemModifier created = orderItemModifierService.createOrderItemModifier(testOrderItemModifier);

        // Then - Should save and return modifier
        assertNotNull(created);
        assertEquals(1L, created.getOrderItemId());
        assertEquals(1L, created.getModifierId());
        assertEquals(BigDecimal.ZERO, created.getPriceAdjustment());
        verify(orderItemModifierRepository, times(1)).save(testOrderItemModifier);
    }

    @Test
    void testCreateOrderItemModifier_WithPriceAdjustment_ShouldCapturePricing() {
        // WHAT: Test creating modifier with price adjustment
        // WHY: Track historical pricing (Add Bacon was +$2 at time of order)
        
        // Given - Modifier with price adjustment
        OrderItemModifier baconModifier = new OrderItemModifier();
        baconModifier.setOrderItemId(1L);
        baconModifier.setModifierId(2L);
        baconModifier.setPriceAdjustment(new BigDecimal("2.00"));
        
        when(orderItemModifierRepository.save(any(OrderItemModifier.class))).thenReturn(baconModifier);

        // When - Create modifier
        OrderItemModifier created = orderItemModifierService.createOrderItemModifier(baconModifier);

        // Then - Should save with price adjustment
        assertEquals(new BigDecimal("2.00"), created.getPriceAdjustment());
    }

    @Test
    void testGetAllOrderItemModifiers_ShouldReturnAllModifiers() {
        // WHAT: Test retrieving all order item modifiers
        // WHY: View all modifier selections across all orders
        
        // Given - Mock returns 2 modifiers
        OrderItemModifier modifier2 = new OrderItemModifier();
        List<OrderItemModifier> modifiers = Arrays.asList(testOrderItemModifier, modifier2);
        when(orderItemModifierRepository.findAll()).thenReturn(modifiers);

        // When - Get all modifiers
        List<OrderItemModifier> result = orderItemModifierService.getAllOrderItemModifiers();

        // Then - Should get both modifiers
        assertEquals(2, result.size());
        verify(orderItemModifierRepository, times(1)).findAll();
    }

    @Test
    void testGetOrderItemModifierById_WhenExists_ShouldReturnModifier() {
        // WHAT: Test finding a specific modifier by ID
        // WHY: Need to load modifier details
        
        // Given - Mock returns the modifier
        when(orderItemModifierRepository.findById(1L)).thenReturn(Optional.of(testOrderItemModifier));

        // When - Get modifier by ID
        Optional<OrderItemModifier> result = orderItemModifierService.getOrderItemModifierById(1L);

        // Then - Should find the modifier
        assertTrue(result.isPresent());
        assertEquals(1L, result.get().getOrderItemId());
        verify(orderItemModifierRepository, times(1)).findById(1L);
    }

    @Test
    void testGetModifiersForOrderItem_ShouldReturnItemModifiers() {
        // WHAT: Test getting all modifiers for a specific order item
        // WHY: See what customer selected (Fries + Add Bacon + Extra Sauce)
        
        // Given - Mock returns modifiers for order item
        OrderItemModifier modifier2 = new OrderItemModifier();
        modifier2.setOrderItemId(1L);
        modifier2.setModifierId(2L);
        modifier2.setPriceAdjustment(new BigDecimal("2.00"));
        
        List<OrderItemModifier> itemModifiers = Arrays.asList(testOrderItemModifier, modifier2);
        when(orderItemModifierRepository.findByOrderItemId(1L)).thenReturn(itemModifiers);

        // When - Get modifiers for order item 1
        List<OrderItemModifier> result = orderItemModifierService.getModifiersForOrderItem(1L);

        // Then - Should get all modifiers for that item
        assertEquals(2, result.size());
        assertEquals(1L, result.get(0).getOrderItemId());
        assertEquals(1L, result.get(1).getOrderItemId());
        verify(orderItemModifierRepository, times(1)).findByOrderItemId(1L);
    }

    @Test
    void testGetOrderItemsWithModifier_ShouldReturnMatchingItems() {
        // WHAT: Test getting all order items that used a specific modifier
        // WHY: Analytics (how many people ordered "Add Bacon"?)
        
        // Given - Mock returns order items with bacon modifier
        List<OrderItemModifier> baconOrders = Arrays.asList(testOrderItemModifier);
        when(orderItemModifierRepository.findByModifierId(2L)).thenReturn(baconOrders);

        // When - Get all order items with bacon
        List<OrderItemModifier> result = orderItemModifierService.getOrderItemsWithModifier(2L);

        // Then - Should get all items with that modifier
        assertEquals(1, result.size());
        verify(orderItemModifierRepository, times(1)).findByModifierId(2L);
    }

    @Test
    void testDeleteOrderItemModifier_ShouldCallRepository() {
        // WHAT: Test deleting an order item modifier
        // WHY: Remove modifier selections (if order item is edited)
        
        // Given - Mock repository
        doNothing().when(orderItemModifierRepository).deleteById(1L);

        // When - Delete modifier
        orderItemModifierService.deleteOrderItemModifier(1L);

        // Then - Repository delete should be called
        verify(orderItemModifierRepository, times(1)).deleteById(1L);
    }

    // ========================================================================
    // Ringing in items WITH modifiers and kitchen notes
    // ========================================================================

    // ---- small builders to keep these tests readable ----

    // Combo Plate ($15.00): Bread Choice (required, choose 1) and Add-Ons (optional, up to 2)
    private static final Long COMBO_PLATE_ID = 1L;

    private MenuItem comboPlate() {
        MenuItem menuItem = new MenuItem();
        menuItem.setMenuItemId(COMBO_PLATE_ID);
        menuItem.setName("Combo Plate");
        menuItem.setPrice(new BigDecimal("15.00"));
        return menuItem;
    }

    private List<ModifierGroupView> comboPlateGroups() {
        ModifierGroupView bread = new ModifierGroupView(2L, "Bread Choice", "Choose your bread", true, 1, List.of(
                new ModifierOptionView(8L, "White Toast", new BigDecimal("0.00")),
                new ModifierOptionView(14L, "French Toast", new BigDecimal("2.00"))));
        ModifierGroupView addOns = new ModifierGroupView(5L, "Add-Ons", "Optional add-ons", false, 2, List.of(
                new ModifierOptionView(22L, "Add Egg", new BigDecimal("2.00")),
                new ModifierOptionView(23L, "Add Avocado", new BigDecimal("2.00")),
                new ModifierOptionView(24L, "Add Chicken", new BigDecimal("4.00"))));
        return List.of(bread, addOns);
    }

    private OrderItem savedItem(Long orderItemId, String status, boolean locked) {
        OrderItem item = new OrderItem();
        item.setOrderItemId(orderItemId);
        item.setOrderId(77L);
        item.setMenuItemId(COMBO_PLATE_ID);
        item.setQuantity(1);
        item.setPrice(new BigDecimal("15.00"));
        item.setStatus(status);
        item.setDelaySeconds(15);
        item.setIsLocked(locked);
        return item;
    }

    private OrderItemModifier modifierRow(Long rowId, Long orderItemId, Long modifierId, String priceAdjustment) {
        OrderItemModifier row = new OrderItemModifier();
        row.setOrderItemModifierId(rowId);
        row.setOrderItemId(orderItemId);
        row.setModifierId(modifierId);
        row.setPriceAdjustment(new BigDecimal(priceAdjustment));
        return row;
    }

    private Modifier modifier(Long modifierId, String name) {
        Modifier modifier = new Modifier();
        modifier.setModifierId(modifierId);
        modifier.setName(name);
        return modifier;
    }

    // Make the fake repository behave like the real one: hand back the item it was given, now with an ID
    private void stubSaveToAssignId(Long newId) {
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> {
            OrderItem item = invocation.getArgument(0);
            item.setOrderItemId(newId);
            return item;
        });
    }

    // The modifier rows that were handed to saveAll().
    // An ArgumentCaptor "catches" the argument a mock was called with so the test can inspect it.
    @SuppressWarnings("unchecked")
    private List<OrderItemModifier> capturedSavedRows() {
        ArgumentCaptor<List<OrderItemModifier>> captor = ArgumentCaptor.forClass(List.class);
        verify(orderItemModifierRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void testRingInItem_ShouldSaveItemAsDraftWithUpchargesInPrice() {
        // WHAT: Test ringing in an item with modifiers and a kitchen note
        // WHY: The price charged must be menu price + upcharges, worked out by the backend

        // Given - Combo Plate with French Toast (+$2) and Add Chicken (+$4)
        when(menuItemRepository.findById(COMBO_PLATE_ID)).thenReturn(Optional.of(comboPlate()));
        when(menuModifierService.getModifierGroupsForMenuItem(COMBO_PLATE_ID)).thenReturn(comboPlateGroups());
        stubSaveToAssignId(100L);

        // When
        OrderItem created = orderItemModifierService.ringInItem(
                new OrderItemRequest(77L, COMBO_PLATE_ID, 1, "  no butter  ", Arrays.asList(24L, 14L)));

        // Then - saved as an unlocked draft; 15.00 + 2.00 + 4.00 = 21.00
        assertEquals(100L, created.getOrderItemId());
        assertEquals(77L, created.getOrderId());
        assertEquals(COMBO_PLATE_ID, created.getMenuItemId());
        assertEquals(1, created.getQuantity());
        assertEquals(new BigDecimal("21.00"), created.getPrice());
        assertEquals("draft", created.getStatus());
        assertFalse(created.getIsLocked());
        assertEquals("no butter", created.getSpecialInstructions());       // Note is trimmed

        // One row per chosen modifier, in picker order (Bread before Add-Ons), each with its upcharge
        List<OrderItemModifier> rows = capturedSavedRows();
        assertEquals(2, rows.size());
        assertEquals(100L, rows.get(0).getOrderItemId());
        assertEquals(14L, rows.get(0).getModifierId());
        assertEquals(new BigDecimal("2.00"), rows.get(0).getPriceAdjustment());
        assertEquals(24L, rows.get(1).getModifierId());
        assertEquals(new BigDecimal("4.00"), rows.get(1).getPriceAdjustment());
    }

    @Test
    void testRingInItem_WithNoteOnly_ShouldKeepMenuPrice() {
        // WHAT: Test ringing in a plain item (no modifier groups) with just a kitchen note
        // WHY: Every item can carry a note, not only items with modifiers

        // Given - a soda has no modifier groups
        MenuItem soda = new MenuItem();
        soda.setMenuItemId(40L);
        soda.setName("Soda");
        soda.setPrice(new BigDecimal("3.00"));
        when(menuItemRepository.findById(40L)).thenReturn(Optional.of(soda));
        when(menuModifierService.getModifierGroupsForMenuItem(40L)).thenReturn(List.of());
        stubSaveToAssignId(101L);

        // When - quantity left out, no modifier list at all
        OrderItem created = orderItemModifierService.ringInItem(
                new OrderItemRequest(77L, 40L, null, "no ice", null));

        // Then
        assertEquals(new BigDecimal("3.00"), created.getPrice());
        assertEquals(1, created.getQuantity());                             // Quantity defaults to 1
        assertEquals("no ice", created.getSpecialInstructions());
        assertTrue(capturedSavedRows().isEmpty());
    }

    @Test
    void testRingInItem_WithBlankNote_ShouldStoreNoNote() {
        // WHAT: Test that a note of only spaces is stored as "no note"
        // WHY: The kitchen ticket should not print an empty note line

        // Given
        when(menuItemRepository.findById(COMBO_PLATE_ID)).thenReturn(Optional.of(comboPlate()));
        when(menuModifierService.getModifierGroupsForMenuItem(COMBO_PLATE_ID)).thenReturn(comboPlateGroups());
        stubSaveToAssignId(100L);

        // When
        OrderItem created = orderItemModifierService.ringInItem(
                new OrderItemRequest(77L, COMBO_PLATE_ID, 1, "   ", List.of(8L)));

        // Then
        assertNull(created.getSpecialInstructions());
    }

    @Test
    void testRingInItem_WhenRequiredGroupNotAnswered_ShouldThrowAndSaveNothing() {
        // WHAT: Test leaving out a required group
        // WHY: The kitchen cannot make a Combo Plate without knowing the bread

        // Given - only an optional add-on was chosen
        when(menuItemRepository.findById(COMBO_PLATE_ID)).thenReturn(Optional.of(comboPlate()));
        when(menuModifierService.getModifierGroupsForMenuItem(COMBO_PLATE_ID)).thenReturn(comboPlateGroups());

        // When & Then
        InvalidModifierSelectionException exception = assertThrows(InvalidModifierSelectionException.class, () ->
                orderItemModifierService.ringInItem(
                        new OrderItemRequest(77L, COMBO_PLATE_ID, 1, null, List.of(22L))));

        assertTrue(exception.getMessage().contains("Bread Choice"));
        verify(orderItemRepository, never()).save(any());
        verify(orderItemModifierRepository, never()).saveAll(anyList());
    }

    @Test
    void testRingInItem_WhenTooManyChosenFromGroup_ShouldThrow() {
        // WHAT: Test picking more than a group's maxSelections
        // WHY: "Bread Choice" allows one bread, "Add-Ons" allows two

        // Given
        when(menuItemRepository.findById(COMBO_PLATE_ID)).thenReturn(Optional.of(comboPlate()));
        when(menuModifierService.getModifierGroupsForMenuItem(COMBO_PLATE_ID)).thenReturn(comboPlateGroups());

        // When & Then - two breads
        InvalidModifierSelectionException twoBreads = assertThrows(InvalidModifierSelectionException.class, () ->
                orderItemModifierService.ringInItem(
                        new OrderItemRequest(77L, COMBO_PLATE_ID, 1, null, List.of(8L, 14L))));
        assertTrue(twoBreads.getMessage().contains("Bread Choice"));

        // ...and three add-ons where two are allowed
        InvalidModifierSelectionException threeAddOns = assertThrows(InvalidModifierSelectionException.class, () ->
                orderItemModifierService.ringInItem(
                        new OrderItemRequest(77L, COMBO_PLATE_ID, 1, null, List.of(8L, 22L, 23L, 24L))));
        assertTrue(threeAddOns.getMessage().contains("Add-Ons"));

        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void testRingInItem_WhenModifierNotOfferedForItem_ShouldThrow() {
        // WHAT: Test sending a modifier that does not belong to this menu item
        // WHY: "Fries Size: Full" (ID 30) makes no sense on a Combo Plate

        // Given
        when(menuItemRepository.findById(COMBO_PLATE_ID)).thenReturn(Optional.of(comboPlate()));
        when(menuModifierService.getModifierGroupsForMenuItem(COMBO_PLATE_ID)).thenReturn(comboPlateGroups());

        // When & Then
        InvalidModifierSelectionException exception = assertThrows(InvalidModifierSelectionException.class, () ->
                orderItemModifierService.ringInItem(
                        new OrderItemRequest(77L, COMBO_PLATE_ID, 1, null, List.of(8L, 30L))));

        assertTrue(exception.getMessage().contains("30"));
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void testRingInItem_WhenSameModifierSentTwice_ShouldCountItOnce() {
        // WHAT: Test a modifier ID that appears twice in the request
        // WHY: A double tap must not charge an upcharge twice

        // Given
        when(menuItemRepository.findById(COMBO_PLATE_ID)).thenReturn(Optional.of(comboPlate()));
        when(menuModifierService.getModifierGroupsForMenuItem(COMBO_PLATE_ID)).thenReturn(comboPlateGroups());
        stubSaveToAssignId(100L);

        // When - French Toast (+$2) sent twice
        OrderItem created = orderItemModifierService.ringInItem(
                new OrderItemRequest(77L, COMBO_PLATE_ID, 1, null, Arrays.asList(14L, 14L)));

        // Then - 15.00 + 2.00 once
        assertEquals(new BigDecimal("17.00"), created.getPrice());
        assertEquals(1, capturedSavedRows().size());
    }

    @Test
    void testRingInItem_WhenMenuItemMissing_ShouldThrowNotFound() {
        // Given
        when(menuItemRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () ->
                orderItemModifierService.ringInItem(
                        new OrderItemRequest(77L, 999L, 1, null, List.of())));

        verify(orderItemRepository, never()).save(any());
    }

    // ========================================================================
    // Changing modifiers and notes on a saved item
    // ========================================================================

    @Test
    void testReplaceModifiersForOrderItem_ShouldSwapModifiersAndRepriceItem() {
        // WHAT: Test changing the modifiers on a saved draft
        // WHY: A server fixes "White Toast" to "French Toast" before sending

        // Given - a saved draft Combo Plate
        OrderItem existing = savedItem(100L, "draft", false);
        when(orderItemRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(menuItemRepository.findById(COMBO_PLATE_ID)).thenReturn(Optional.of(comboPlate()));
        when(menuModifierService.getModifierGroupsForMenuItem(COMBO_PLATE_ID)).thenReturn(comboPlateGroups());
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When - now French Toast (+$2) with an allergy note
        OrderItem updated = orderItemModifierService.replaceModifiersForOrderItem(
                100L, new OrderItemModifiersRequest("nut allergy", List.of(14L)));

        // Then - old modifiers removed, new one saved, price is 15.00 + 2.00
        verify(orderItemModifierRepository, times(1)).deleteByOrderItemId(100L);
        List<OrderItemModifier> rows = capturedSavedRows();
        assertEquals(1, rows.size());
        assertEquals(100L, rows.get(0).getOrderItemId());
        assertEquals(14L, rows.get(0).getModifierId());

        assertEquals(new BigDecimal("17.00"), updated.getPrice());
        assertEquals("nut allergy", updated.getSpecialInstructions());
        assertNull(updated.getDelayExpiresAt());                            // A draft has no timer to reset
    }

    @Test
    void testReplaceModifiersForOrderItem_WhenInLimbo_ShouldRestartEditWindow() {
        // WHAT: Test changing an item while its edit timer is running
        // WHY: A changed item gets a fresh window so the kitchen never receives it half-edited

        // Given - an item in limbo with 3 seconds left
        OrderItem existing = savedItem(100L, "limbo", false);
        existing.setDelayExpiresAt(LocalDateTime.now().plusSeconds(3));
        when(orderItemRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(menuItemRepository.findById(COMBO_PLATE_ID)).thenReturn(Optional.of(comboPlate()));
        when(menuModifierService.getModifierGroupsForMenuItem(COMBO_PLATE_ID)).thenReturn(comboPlateGroups());
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        OrderItem updated = orderItemModifierService.replaceModifiersForOrderItem(
                100L, new OrderItemModifiersRequest(null, List.of(8L)));

        // Then - expiry is pushed out to a full window again (15 seconds + 1 for rendering)
        assertEquals("limbo", updated.getStatus());
        assertTrue(updated.getDelayExpiresAt().isAfter(LocalDateTime.now().plusSeconds(14)));
        assertTrue(updated.getDelayExpiresAt().isBefore(LocalDateTime.now().plusSeconds(17)));

        // And the rest of the order's items still counting down get the same fresh window
        // (order 77 is the order savedItem() puts the item on)
        verify(orderItemService, times(1)).restartEditWindow(77L);
    }

    @Test
    void testReplaceModifiersForOrderItem_WhenDraft_ShouldNotTouchTheEditWindow() {
        // WHAT: Test changing an item that has not been sent yet
        // WHY: No countdown is running for a draft, so there is nothing to restart

        // Given - a saved draft
        OrderItem existing = savedItem(100L, "draft", false);
        when(orderItemRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(menuItemRepository.findById(COMBO_PLATE_ID)).thenReturn(Optional.of(comboPlate()));
        when(menuModifierService.getModifierGroupsForMenuItem(COMBO_PLATE_ID)).thenReturn(comboPlateGroups());
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When
        OrderItem updated = orderItemModifierService.replaceModifiersForOrderItem(
                100L, new OrderItemModifiersRequest(null, List.of(8L)));

        // Then
        assertNull(updated.getDelayExpiresAt());
        verify(orderItemService, never()).restartEditWindow(any());
    }

    @Test
    void testReplaceModifiersForOrderItem_WhenLocked_ShouldThrowAndChangeNothing() {
        // WHAT: Test changing an item that already went to the kitchen
        // WHY: The kitchen may already be cooking it - too late to edit

        // Given
        when(orderItemRepository.findById(100L)).thenReturn(Optional.of(savedItem(100L, "pending", true)));

        // When & Then
        ItemLockedException exception = assertThrows(ItemLockedException.class, () ->
                orderItemModifierService.replaceModifiersForOrderItem(
                        100L, new OrderItemModifiersRequest("late change", List.of(14L))));

        assertTrue(exception.getMessage().contains("locked item"));
        verify(orderItemModifierRepository, never()).deleteByOrderItemId(any());
        verify(orderItemModifierRepository, never()).saveAll(anyList());
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void testReplaceModifiersForOrderItem_WhenSelectionInvalid_ShouldKeepOldModifiers() {
        // WHAT: Test replacing with a selection that breaks the rules
        // WHY: A rejected change must not wipe out the modifiers the item already has

        // Given - the new selection leaves out the required bread
        when(orderItemRepository.findById(100L)).thenReturn(Optional.of(savedItem(100L, "draft", false)));
        when(menuItemRepository.findById(COMBO_PLATE_ID)).thenReturn(Optional.of(comboPlate()));
        when(menuModifierService.getModifierGroupsForMenuItem(COMBO_PLATE_ID)).thenReturn(comboPlateGroups());

        // When & Then
        assertThrows(InvalidModifierSelectionException.class, () ->
                orderItemModifierService.replaceModifiersForOrderItem(
                        100L, new OrderItemModifiersRequest(null, List.of())));

        verify(orderItemModifierRepository, never()).deleteByOrderItemId(any());
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void testReplaceModifiersForOrderItem_WhenItemMissing_ShouldThrowNotFound() {
        // Given
        when(orderItemRepository.findById(999L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () ->
                orderItemModifierService.replaceModifiersForOrderItem(
                        999L, new OrderItemModifiersRequest(null, List.of())));
    }

    // ========================================================================
    // Reading modifiers for the screen (with names)
    // ========================================================================

    @Test
    void testGetModifierViewsForOrderItems_ShouldGroupModifiersByItemWithNames() {
        // WHAT: Test looking up the modifiers for many order items at once
        // WHY: The kitchen ticket needs names ("French Toast"), not modifier IDs

        // Given - item 100 has two modifiers, item 101 has one; rows arrive out of order
        when(orderItemModifierRepository.findByOrderItemIdIn(anyCollection())).thenReturn(Arrays.asList(
                modifierRow(3L, 101L, 8L, "0.00"),
                modifierRow(2L, 100L, 24L, "4.00"),
                modifierRow(1L, 100L, 14L, "2.00")));
        when(modifierRepository.findAllById(any())).thenReturn(Arrays.asList(
                modifier(8L, "White Toast"), modifier(14L, "French Toast"), modifier(24L, "Add Chicken")));

        // When - item 102 has no modifiers
        Map<Long, List<OrderItemModifierView>> result =
                orderItemModifierService.getModifierViewsForOrderItems(List.of(100L, 101L, 102L));

        // Then - each item got only its own modifiers, in the order they were saved
        assertEquals(2, result.size());
        assertEquals(2, result.get(100L).size());
        assertEquals("French Toast", result.get(100L).get(0).name());
        assertEquals(new BigDecimal("2.00"), result.get(100L).get(0).priceAdjustment());
        assertEquals("Add Chicken", result.get(100L).get(1).name());
        assertEquals("White Toast", result.get(101L).get(0).name());
        assertFalse(result.containsKey(102L));

        // The whole point: ONE query for all items, not one per item
        verify(orderItemModifierRepository, times(1)).findByOrderItemIdIn(anyCollection());
        verify(orderItemModifierRepository, never()).findByOrderItemId(any());
    }

    @Test
    void testGetModifierViewsForOrderItems_WhenNoItems_ShouldSkipQueries() {
        // When
        Map<Long, List<OrderItemModifierView>> result = orderItemModifierService.getModifierViewsForOrderItems(List.of());

        // Then - empty map and no wasted queries
        assertTrue(result.isEmpty());
        verifyNoInteractions(orderItemModifierRepository, modifierRepository);
    }

    @Test
    void testGetModifierViewsForOrderItems_WhenModifierWasDeleted_ShouldUsePlaceholderName() {
        // Given - the modifier was removed from the menu after the order was placed
        when(orderItemModifierRepository.findByOrderItemIdIn(anyCollection())).thenReturn(Arrays.asList(
                modifierRow(1L, 100L, 999L, "1.00")));
        when(modifierRepository.findAllById(any())).thenReturn(List.of());

        // When
        Map<Long, List<OrderItemModifierView>> result = orderItemModifierService.getModifierViewsForOrderItems(List.of(100L));

        // Then - a placeholder name instead of a crash
        assertEquals("Modifier 999", result.get(100L).get(0).name());
    }

    @Test
    void testGetModifierViewsForOrder_ShouldReturnModifiersForEveryItemOnTheOrder() {
        // WHAT: Test loading the modifiers for one whole order
        // WHY: The order screen shows them when a server reopens a table

        // Given - order 77 has items 100 and 101
        when(orderItemRepository.findByOrderId(77L)).thenReturn(Arrays.asList(
                savedItem(101L, "draft", false), savedItem(100L, "pending", true)));
        when(orderItemModifierRepository.findByOrderItemIdIn(anyCollection())).thenReturn(Arrays.asList(
                modifierRow(1L, 100L, 14L, "2.00"),
                modifierRow(2L, 101L, 8L, "0.00")));
        when(modifierRepository.findAllById(any())).thenReturn(Arrays.asList(
                modifier(8L, "White Toast"), modifier(14L, "French Toast")));

        // When
        List<OrderItemModifierView> result = orderItemModifierService.getModifierViewsForOrder(77L);

        // Then - one flat list, each entry naming the order item it belongs to
        assertEquals(2, result.size());
        assertEquals(100L, result.get(0).orderItemId());
        assertEquals("French Toast", result.get(0).name());
        assertEquals(101L, result.get(1).orderItemId());
        assertEquals("White Toast", result.get(1).name());
    }

    @Test
    void testRingInItem_WhenMenuItemIs86d_ShouldThrowAndSaveNothing() {
        // WHAT: Test ringing in an 86'd item that has modifiers
        // WHY: Same rule as a plain item - the kitchen is out of it

        // Given - the Combo Plate is 86'd
        MenuItem menuItem = comboPlate();
        menuItem.setIs86d(true);
        when(menuItemRepository.findById(COMBO_PLATE_ID)).thenReturn(Optional.of(menuItem));

        // When & Then
        com.notapos.exception.ItemUnavailableException exception = assertThrows(
                com.notapos.exception.ItemUnavailableException.class,
                () -> orderItemModifierService.ringInItem(
                        new com.notapos.dto.OrderItemRequest(77L, COMBO_PLATE_ID, 1, null, List.of(8L))));
        assertTrue(exception.getMessage().contains("Combo Plate"));
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void testRingInItem_ShouldCheckTheOrderIsOpenBeforeAnythingElse() {
        // WHAT: Test that ring-in asks "is this order still open?" first
        // WHY: If the check was closed on another terminal, nothing may be looked up or saved

        // Given - the open-order check refuses
        when(orderItemService.requireOpenOrder(77L)).thenThrow(
                new com.notapos.exception.OrderNotOpenException("This check was already closed."));

        // When & Then
        assertThrows(com.notapos.exception.OrderNotOpenException.class, () -> orderItemModifierService.ringInItem(
                new com.notapos.dto.OrderItemRequest(77L, COMBO_PLATE_ID, 1, null, List.of(8L))));
        verify(menuItemRepository, never()).findById(any());
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void testRingInItem_WhenTheMenuItemIsOffTheMenu_ShouldThrowAndSaveNothing() {
        // WHAT: Test ringing in an item that was deactivated
        // WHY: A screen that loaded the menu before the item was removed could still send it

        // Given - the Combo Plate has been taken off the menu
        MenuItem menuItem = comboPlate();
        menuItem.setIsActive(false);
        when(menuItemRepository.findById(COMBO_PLATE_ID)).thenReturn(Optional.of(menuItem));

        // When & Then
        com.notapos.exception.ItemUnavailableException exception = assertThrows(
                com.notapos.exception.ItemUnavailableException.class,
                () -> orderItemModifierService.ringInItem(
                        new com.notapos.dto.OrderItemRequest(77L, COMBO_PLATE_ID, 1, null, List.of(8L))));
        assertTrue(exception.getMessage().contains("no longer on the menu"));
        verify(orderItemRepository, never()).save(any());
    }

    @Test
    void testRingInItem_ForAnItemWithNoModifierGroups_ShouldSaveItAtTheMenuPrice() {
        // WHAT: Test ringing in a plain item (no modifiers to choose, none chosen)
        // WHY: Plain items used to go through a separate path that saved the browser's
        //      price. They now come through here and get the menu price like everything else.

        // Given - a $6.00 item with no modifier groups
        MenuItem fries = new MenuItem();
        fries.setMenuItemId(2L);
        fries.setName("Fries");
        fries.setPrice(new BigDecimal("6.00"));
        when(menuItemRepository.findById(2L)).thenReturn(Optional.of(fries));
        when(menuModifierService.getModifierGroupsForMenuItem(2L)).thenReturn(List.of());
        when(orderItemRepository.save(any(OrderItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // When - with no modifier list at all, as a plain item sends
        OrderItem saved = orderItemModifierService.ringInItem(
                new com.notapos.dto.OrderItemRequest(77L, 2L, 1, null, null));

        // Then
        assertEquals(new BigDecimal("6.00"), saved.getPrice());
        assertEquals("draft", saved.getStatus());
        assertFalse(saved.getIsLocked());
    }

    @Test
    void testReplaceModifiersForOrderItem_OnAClosedOrder_ShouldThrowAndChangeNothing() {
        // WHAT: Test changing modifiers on an item whose check was already paid
        // WHY: Changing modifiers changes the price, and a paid check must not change

        // Given - the item's order (77) is closed
        OrderItem existing = savedItem(100L, "draft", false);
        when(orderItemRepository.findById(100L)).thenReturn(Optional.of(existing));
        when(orderItemService.requireOpenOrder(77L)).thenThrow(
                new com.notapos.exception.OrderNotOpenException("This check was already closed."));

        // When & Then
        assertThrows(com.notapos.exception.OrderNotOpenException.class,
                () -> orderItemModifierService.replaceModifiersForOrderItem(100L, new OrderItemModifiersRequest(null, List.of(14L))));
        verify(orderItemModifierRepository, never()).deleteByOrderItemId(any());
        verify(orderItemRepository, never()).save(any());
        assertEquals(new BigDecimal("15.00"), existing.getPrice());
    }
}
