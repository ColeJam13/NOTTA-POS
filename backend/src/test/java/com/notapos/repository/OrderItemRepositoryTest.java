package com.notapos.repository;

import com.notapos.entity.OrderItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for OrderItemRepository.
 * 
 * Tests database queries for order items - the signature delay timer feature.
 * 
 * @author CJ
 */

@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.NONE)
class OrderItemRepositoryTest {

    @Autowired
    private OrderItemRepository orderItemRepository;

    private OrderItem draftItem;
    private OrderItem pendingItem;
    private OrderItem firedItem;

    @BeforeEach
    void setUp() {
        // Clear database before each test
        orderItemRepository.deleteAll();

        // Create draft item (not sent yet)
        draftItem = new OrderItem();
        draftItem.setOrderId(1L);
        draftItem.setMenuItemId(1L);
        draftItem.setQuantity(1);
        draftItem.setPrice(new BigDecimal("12.99"));
        draftItem.setStatus("draft");
        draftItem.setDelaySeconds(15);
        draftItem.setIsLocked(false);
        draftItem = orderItemRepository.save(draftItem);

        // Create pending item (sent, timer running)
        pendingItem = new OrderItem();
        pendingItem.setOrderId(1L);
        pendingItem.setMenuItemId(2L);
        pendingItem.setQuantity(2);
        pendingItem.setPrice(new BigDecimal("8.99"));
        pendingItem.setStatus("pending");
        pendingItem.setDelaySeconds(15);
        pendingItem.setDelayExpiresAt(LocalDateTime.now().plusSeconds(15));
        pendingItem.setIsLocked(false);
        pendingItem = orderItemRepository.save(pendingItem);

        // Create fired item (locked, sent to kitchen)
        firedItem = new OrderItem();
        firedItem.setOrderId(2L);
        firedItem.setMenuItemId(3L);
        firedItem.setQuantity(1);
        firedItem.setPrice(new BigDecimal("15.99"));
        firedItem.setStatus("fired");
        firedItem.setDelaySeconds(15);
        firedItem.setDelayExpiresAt(LocalDateTime.now().minusSeconds(10));
        firedItem.setIsLocked(true);
        firedItem.setFiredAt(LocalDateTime.now().minusSeconds(5));
        firedItem = orderItemRepository.save(firedItem);
    }

    @Test
    void testSave_ShouldPersistOrderItem() {
        // WHAT: Test saving a new order item to database
        // WHY: Ensure basic create operation works
        
        // Given - New order item
        OrderItem newItem = new OrderItem();
        newItem.setOrderId(3L);
        newItem.setMenuItemId(4L);
        newItem.setQuantity(3);
        newItem.setPrice(new BigDecimal("6.99"));
        newItem.setStatus("draft");
        newItem.setDelaySeconds(15);
        newItem.setIsLocked(false);

        // When - Save to database
        OrderItem saved = orderItemRepository.save(newItem);

        // Then - Should persist with generated ID
        assertNotNull(saved.getOrderItemId());
        assertEquals(3L, saved.getOrderId());
        assertEquals("draft", saved.getStatus());
    }

    @Test
    void testFindById_WhenExists_ShouldReturnItem() {
        // WHAT: Test finding order item by ID
        // WHY: Need to load specific items for editing during delay window
        
        // Given - Item exists in database (from setUp)
        
        // When - Find by ID
        Optional<OrderItem> result = orderItemRepository.findById(draftItem.getOrderItemId());

        // Then - Should find the item
        assertTrue(result.isPresent());
        assertEquals("draft", result.get().getStatus());
        assertEquals(1L, result.get().getOrderId());
    }

    @Test
    void testFindById_WhenNotExists_ShouldReturnEmpty() {
        // WHAT: Test finding non-existent order item
        // WHY: Handle missing items gracefully
        
        // Given - Non-existent ID
        
        // When - Try to find
        Optional<OrderItem> result = orderItemRepository.findById(999L);

        // Then - Should return empty
        assertFalse(result.isPresent());
    }

    @Test
    void testFindAll_ShouldReturnAllItems() {
        // WHAT: Test retrieving all order items
        // WHY: Get complete view of all orders
        
        // Given - 3 items in database (from setUp)
        
        // When - Find all
        List<OrderItem> items = orderItemRepository.findAll();

        // Then - Should get all 3 items
        assertEquals(3, items.size());
    }

    @Test
    void testFindByOrderId_ShouldReturnItemsForOrder() {
        // WHAT: Test finding all items for a specific order
        // WHY: Display order details showing all items on the ticket
        
        // Given - Order 1 has 2 items, Order 2 has 1 item (from setUp)
        
        // When - Find items for Order 1
        List<OrderItem> order1Items = orderItemRepository.findByOrderId(1L);

        // Then - Should get 2 items for Order 1
        assertEquals(2, order1Items.size());
        assertTrue(order1Items.stream().allMatch(item -> item.getOrderId().equals(1L)));
    }

    @Test
    void testFindByStatus_Draft_ShouldReturnDraftItems() {
        // WHAT: Test finding items in draft status
        // WHY: Show servers which items haven't been sent yet
        
        // Given - 1 draft item exists (from setUp)
        
        // When - Find draft items
        List<OrderItem> drafts = orderItemRepository.findByStatus("draft");

        // Then - Should get only draft items
        assertEquals(1, drafts.size());
        assertEquals("draft", drafts.get(0).getStatus());
    }

    @Test
    void testFindByStatus_Pending_ShouldReturnPendingItems() {
        // WHAT: Test finding items in pending status
        // WHY: Show which items are in delay timer window (editable)
        
        // Given - 1 pending item exists (from setUp)
        
        // When - Find pending items
        List<OrderItem> pending = orderItemRepository.findByStatus("pending");

        // Then - Should get only pending items
        assertEquals(1, pending.size());
        assertEquals("pending", pending.get(0).getStatus());
        assertNotNull(pending.get(0).getDelayExpiresAt());
    }

    @Test
    void testFindByStatus_Fired_ShouldReturnFiredItems() {
        // WHAT: Test finding items in fired status
        // WHY: Show locked items that are cooking in kitchen
        
        // Given - 1 fired item exists (from setUp)
        
        // When - Find fired items
        List<OrderItem> fired = orderItemRepository.findByStatus("fired");

        // Then - Should get only fired items
        assertEquals(1, fired.size());
        assertEquals("fired", fired.get(0).getStatus());
        assertTrue(fired.get(0).getIsLocked());
    }

    @Test
    void testFindByOrderIdAndStatus_ShouldReturnMatchingItems() {
        // WHAT: Test finding items by both order ID and status
        // WHY: Get draft items for order when server clicks "Send"
        
        // Given - Order 1 has 1 draft and 1 pending (from setUp)
        
        // When - Find draft items for Order 1
        List<OrderItem> draftItems = orderItemRepository.findByOrderIdAndStatus(1L, "draft");

        // Then - Should get only the draft item
        assertEquals(1, draftItems.size());
        assertEquals("draft", draftItems.get(0).getStatus());
        assertEquals(1L, draftItems.get(0).getOrderId());
    }

    @Test
    void testFindExpiredUnlockedItems_ShouldReturnExpiredItems() {
        // WHAT: Test finding items whose delay has expired but aren't locked
        // WHY: Background job uses this to auto-lock and fire items
        
        // Given - Create an expired but unlocked item
        OrderItem expiredItem = new OrderItem();
        expiredItem.setOrderId(3L);
        expiredItem.setMenuItemId(5L);
        expiredItem.setQuantity(1);
        expiredItem.setPrice(new BigDecimal("10.99"));
        expiredItem.setStatus("pending");
        expiredItem.setDelaySeconds(15);
        expiredItem.setDelayExpiresAt(LocalDateTime.now().minusSeconds(5)); // Expired 5 seconds ago
        expiredItem.setIsLocked(false);
        orderItemRepository.save(expiredItem);
        
        // When - Find expired unlocked items
        List<OrderItem> expired = orderItemRepository.findExpiredUnlockedItems(LocalDateTime.now(), false);

        // Then - Should find the expired item
        assertTrue(expired.size() >= 1);
        assertTrue(expired.stream().anyMatch(item -> 
            item.getDelayExpiresAt().isBefore(LocalDateTime.now()) && !item.getIsLocked()
        ));
    }

    @Test
    void testFindByIsLocked_False_ShouldReturnUnlockedItems() {
        // WHAT: Test finding all unlocked items
        // WHY: Show which items can still be edited
        
        // Given - 2 unlocked items (draft and pending from setUp)
        
        // When - Find unlocked items
        List<OrderItem> unlocked = orderItemRepository.findByIsLocked(false);

        // Then - Should get 2 unlocked items
        assertEquals(2, unlocked.size());
        assertTrue(unlocked.stream().allMatch(item -> !item.getIsLocked()));
    }

    @Test
    void testFindByIsLocked_True_ShouldReturnLockedItems() {
        // WHAT: Test finding all locked items
        // WHY: Show which items are in kitchen (can't be changed)
        
        // Given - 1 locked item (fired from setUp)
        
        // When - Find locked items
        List<OrderItem> locked = orderItemRepository.findByIsLocked(true);

        // Then - Should get 1 locked item
        assertEquals(1, locked.size());
        assertTrue(locked.get(0).getIsLocked());
    }

    @Test
    void testDeleteById_ShouldRemoveItem() {
        // WHAT: Test deleting an order item
        // WHY: Remove mistakenly added items before sending to kitchen
        
        // Given - Draft item exists
        Long itemId = draftItem.getOrderItemId();
        
        // When - Delete the item
        orderItemRepository.deleteById(itemId);

        // Then - Item should no longer exist
        Optional<OrderItem> deleted = orderItemRepository.findById(itemId);
        assertFalse(deleted.isPresent());
    }

    @Test
    void testUpdate_ShouldModifyExistingItem() {
        // WHAT: Test updating an order item's fields
        // WHY: Edit item details during delay timer window
        
        // Given - Pending item exists
        Long itemId = pendingItem.getOrderItemId();
        
        // When - Update quantity and special instructions
        pendingItem.setQuantity(5);
        pendingItem.setSpecialInstructions("No onions please");
        OrderItem updated = orderItemRepository.save(pendingItem);

        // Then - Changes should persist
        OrderItem reloaded = orderItemRepository.findById(itemId).orElseThrow();
        assertEquals(5, reloaded.getQuantity());
        assertEquals("No onions please", reloaded.getSpecialInstructions());
    }
}