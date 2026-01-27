package com.notapos.repository;

import com.notapos.entity.OrderItem;
import com.notapos.entity.Order;
import com.notapos.entity.MenuItem;
import com.notapos.entity.RestaurantTable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for OrderItemRepository.
 * 
 * Tests database queries for order items - the signature delay timer feature.
 * Uses PostgreSQL Testcontainer.
 * 
 * CHANGES FROM ORIGINAL:
 * - Now extends BaseRepositoryTest (provides PostgreSQL container)
 * - Removed @DataJpaTest, @AutoConfigureTestDatabase (inherited from base)
 * - Creates actual Order, MenuItem, and RestaurantTable entities first (proper foreign key handling)
 * - Tests now run against real PostgreSQL 16 in Docker
 * 
 * @author CJ
 */
class OrderItemRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private OrderItemRepository orderItemRepository;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private MenuItemRepository menuItemRepository;
    
    @Autowired
    private TableRepository tableRepository;

    private RestaurantTable table1;
    private RestaurantTable table2;
    private RestaurantTable table3;
    private Order order1;
    private Order order2;
    private Order order3;
    private MenuItem menuItem1;
    private MenuItem menuItem2;
    private MenuItem menuItem3;
    private MenuItem menuItem4;
    private MenuItem menuItem5;
    private OrderItem draftItem;
    private OrderItem pendingItem;
    private OrderItem firedItem;

    @BeforeEach
    void setUp() {
        // Clear database before each test (in proper order due to foreign keys)
        orderItemRepository.deleteAll();
        orderRepository.deleteAll();
        menuItemRepository.deleteAll();
        tableRepository.deleteAll();

        // Create tables first
        table1 = new RestaurantTable();
        table1.setTableNumber("F1");
        table1.setSection("Front");
        table1.setSeatCount(2);
        table1.setStatus("occupied");
        table1 = tableRepository.save(table1);

        table2 = new RestaurantTable();
        table2.setTableNumber("F2");
        table2.setSection("Front");
        table2.setSeatCount(4);
        table2.setStatus("occupied");
        table2 = tableRepository.save(table2);

        table3 = new RestaurantTable();
        table3.setTableNumber("B1");
        table3.setSection("Back");
        table3.setSeatCount(4);
        table3.setStatus("available");
        table3 = tableRepository.save(table3);

        // Create menu items
        menuItem1 = new MenuItem();
        menuItem1.setName("Burger");
        menuItem1.setDescription("Classic burger");
        menuItem1.setPrice(new BigDecimal("12.99"));
        menuItem1.setCategory("Savory");
        menuItem1.setPrepStationId(null);
        menuItem1.setIsActive(true);
        menuItem1 = menuItemRepository.save(menuItem1);

        menuItem2 = new MenuItem();
        menuItem2.setName("Fries");
        menuItem2.setDescription("French fries");
        menuItem2.setPrice(new BigDecimal("8.99"));
        menuItem2.setCategory("Savory");
        menuItem2.setPrepStationId(null);
        menuItem2.setIsActive(true);
        menuItem2 = menuItemRepository.save(menuItem2);

        menuItem3 = new MenuItem();
        menuItem3.setName("Salad");
        menuItem3.setDescription("Garden salad");
        menuItem3.setPrice(new BigDecimal("15.99"));
        menuItem3.setCategory("Savory");
        menuItem3.setPrepStationId(null);
        menuItem3.setIsActive(true);
        menuItem3 = menuItemRepository.save(menuItem3);

        menuItem4 = new MenuItem();
        menuItem4.setName("Pizza");
        menuItem4.setDescription("Cheese pizza");
        menuItem4.setPrice(new BigDecimal("6.99"));
        menuItem4.setCategory("Savory");
        menuItem4.setPrepStationId(null);
        menuItem4.setIsActive(true);
        menuItem4 = menuItemRepository.save(menuItem4);

        menuItem5 = new MenuItem();
        menuItem5.setName("Soda");
        menuItem5.setDescription("Soft drink");
        menuItem5.setPrice(new BigDecimal("10.99"));
        menuItem5.setCategory("Beverage");
        menuItem5.setPrepStationId(null);
        menuItem5.setIsActive(true);
        menuItem5 = menuItemRepository.save(menuItem5);

        // Create orders
        order1 = new Order();
        order1.setTableId(table1.getTableId());
        order1.setOrderType("dine_in");
        order1.setStatus("open");
        order1.setSubtotal(new BigDecimal("25.00"));
        order1.setTax(new BigDecimal("2.00"));
        order1.setTotal(new BigDecimal("27.00"));
        order1 = orderRepository.save(order1);

        order2 = new Order();
        order2.setTableId(table2.getTableId());
        order2.setOrderType("dine_in");
        order2.setStatus("open");
        order2.setSubtotal(new BigDecimal("15.99"));
        order2.setTax(new BigDecimal("1.28"));
        order2.setTotal(new BigDecimal("17.27"));
        order2 = orderRepository.save(order2);

        order3 = new Order();
        order3.setTableId(table3.getTableId());
        order3.setOrderType("takeout");
        order3.setStatus("open");
        order3.setSubtotal(new BigDecimal("10.99"));
        order3.setTax(new BigDecimal("0.88"));
        order3.setTotal(new BigDecimal("11.87"));
        order3 = orderRepository.save(order3);

        // Now create order items with valid foreign keys
        // Create draft item (not sent yet)
        draftItem = new OrderItem();
        draftItem.setOrderId(order1.getOrderId());
        draftItem.setMenuItemId(menuItem1.getMenuItemId());
        draftItem.setQuantity(1);
        draftItem.setPrice(new BigDecimal("12.99"));
        draftItem.setStatus("draft");
        draftItem.setDelaySeconds(15);
        draftItem.setIsLocked(false);
        draftItem = orderItemRepository.save(draftItem);

        // Create pending item (sent, timer running)
        pendingItem = new OrderItem();
        pendingItem.setOrderId(order1.getOrderId());
        pendingItem.setMenuItemId(menuItem2.getMenuItemId());
        pendingItem.setQuantity(2);
        pendingItem.setPrice(new BigDecimal("8.99"));
        pendingItem.setStatus("pending");
        pendingItem.setDelaySeconds(15);
        pendingItem.setDelayExpiresAt(LocalDateTime.now().plusSeconds(15));
        pendingItem.setIsLocked(false);
        pendingItem = orderItemRepository.save(pendingItem);

        // Create fired item (locked, sent to kitchen)
        firedItem = new OrderItem();
        firedItem.setOrderId(order2.getOrderId());
        firedItem.setMenuItemId(menuItem3.getMenuItemId());
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
        newItem.setOrderId(order3.getOrderId());
        newItem.setMenuItemId(menuItem4.getMenuItemId());
        newItem.setQuantity(3);
        newItem.setPrice(new BigDecimal("6.99"));
        newItem.setStatus("draft");
        newItem.setDelaySeconds(15);
        newItem.setIsLocked(false);

        // When - Save to database
        OrderItem saved = orderItemRepository.save(newItem);

        // Then - Should persist with generated ID
        assertNotNull(saved.getOrderItemId());
        assertEquals(order3.getOrderId(), saved.getOrderId());
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
        assertEquals(order1.getOrderId(), result.get().getOrderId());
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
        List<OrderItem> order1Items = orderItemRepository.findByOrderId(order1.getOrderId());

        // Then - Should get 2 items for Order 1
        assertEquals(2, order1Items.size());
        assertTrue(order1Items.stream().allMatch(item -> item.getOrderId().equals(order1.getOrderId())));
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
        List<OrderItem> draftItems = orderItemRepository.findByOrderIdAndStatus(order1.getOrderId(), "draft");

        // Then - Should get only the draft item
        assertEquals(1, draftItems.size());
        assertEquals("draft", draftItems.get(0).getStatus());
        assertEquals(order1.getOrderId(), draftItems.get(0).getOrderId());
    }

    @Test
    void testFindExpiredUnlockedItems_ShouldReturnExpiredItems() {
        // WHAT: Test finding items whose delay has expired but aren't locked
        // WHY: Background job uses this to auto-lock and fire items
        
        // Given - Create an expired but unlocked item
        OrderItem expiredItem = new OrderItem();
        expiredItem.setOrderId(order3.getOrderId());
        expiredItem.setMenuItemId(menuItem5.getMenuItemId());
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