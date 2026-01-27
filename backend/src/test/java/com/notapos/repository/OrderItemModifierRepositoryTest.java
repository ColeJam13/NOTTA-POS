package com.notapos.repository;

import com.notapos.entity.OrderItemModifier;
import com.notapos.entity.OrderItem;
import com.notapos.entity.Order;
import com.notapos.entity.MenuItem;
import com.notapos.entity.Modifier;
import com.notapos.entity.ModifierGroup;
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
 * Repository tests for OrderItemModifierRepository.
 * 
 * Tests database queries for order item to modifier relationships using PostgreSQL Testcontainer.
 * 
 * CHANGES FROM ORIGINAL:
 * - Now extends BaseRepositoryTest (provides PostgreSQL container)
 * - Removed @DataJpaTest, @AutoConfigureTestDatabase, @ActiveProfiles (inherited from base)
 * - Creates complete entity hierarchy: Table → Order → OrderItem, ModifierGroup → Modifier
 * - Tests now run against real PostgreSQL 16 in Docker
 * 
 * This is the most complex repository test - requires 5 different entity types!
 * 
 * @author CJ
 */
class OrderItemModifierRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private OrderItemModifierRepository orderItemModifierRepository;
    
    @Autowired
    private OrderItemRepository orderItemRepository;
    
    @Autowired
    private OrderRepository orderRepository;
    
    @Autowired
    private MenuItemRepository menuItemRepository;
    
    @Autowired
    private ModifierRepository modifierRepository;
    
    @Autowired
    private ModifierGroupRepository modifierGroupRepository;
    
    @Autowired
    private TableRepository tableRepository;

    // Entity references
    private RestaurantTable table1;
    private RestaurantTable table2;
    private Order order1;
    private Order order2;
    private MenuItem menuItem1;
    private MenuItem menuItem2;
    private MenuItem menuItem3;
    private ModifierGroup sidesGroup;
    private ModifierGroup proteinGroup;
    private Modifier friesModifier;
    private Modifier addBaconModifier;
    private Modifier saladModifier;
    private Modifier addAvocadoModifier;
    private OrderItem orderItem1;
    private OrderItem orderItem2;
    private OrderItem orderItem3;
    private OrderItemModifier modifier1;
    private OrderItemModifier modifier2;
    private OrderItemModifier modifier3;

    @BeforeEach
    void setUp() {
        // Clear database before each test (in proper order due to foreign keys)
        orderItemModifierRepository.deleteAll();
        orderItemRepository.deleteAll();
        modifierRepository.deleteAll();
        modifierGroupRepository.deleteAll();
        orderRepository.deleteAll();
        menuItemRepository.deleteAll();
        tableRepository.deleteAll();

        // Step 1: Create tables
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

        // Step 2: Create orders
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
        order2.setSubtotal(new BigDecimal("15.00"));
        order2.setTax(new BigDecimal("1.20"));
        order2.setTotal(new BigDecimal("16.20"));
        order2 = orderRepository.save(order2);

        // Step 3: Create menu items
        menuItem1 = new MenuItem();
        menuItem1.setName("Chicken Cutty");
        menuItem1.setDescription("Buttermilk fried chicken");
        menuItem1.setPrice(new BigDecimal("17.00"));
        menuItem1.setCategory("Savory");
        menuItem1.setPrepStationId(null);
        menuItem1.setIsActive(true);
        menuItem1 = menuItemRepository.save(menuItem1);

        menuItem2 = new MenuItem();
        menuItem2.setName("Burger");
        menuItem2.setDescription("Classic burger");
        menuItem2.setPrice(new BigDecimal("12.00"));
        menuItem2.setCategory("Savory");
        menuItem2.setPrepStationId(null);
        menuItem2.setIsActive(true);
        menuItem2 = menuItemRepository.save(menuItem2);

        menuItem3 = new MenuItem();
        menuItem3.setName("Pancakes");
        menuItem3.setDescription("Fluffy pancakes");
        menuItem3.setPrice(new BigDecimal("10.00"));
        menuItem3.setCategory("Sweet");
        menuItem3.setPrepStationId(null);
        menuItem3.setIsActive(true);
        menuItem3 = menuItemRepository.save(menuItem3);

        // Step 4: Create modifier groups
        sidesGroup = new ModifierGroup();
        sidesGroup.setName("Choose a Side");
        sidesGroup.setDescription("Select one side dish");
        sidesGroup.setIsRequired(true);
        sidesGroup.setMaxSelections(1);
        sidesGroup.setIsActive(true);
        sidesGroup = modifierGroupRepository.save(sidesGroup);

        proteinGroup = new ModifierGroup();
        proteinGroup.setName("Add Protein");
        proteinGroup.setDescription("Optional protein additions");
        proteinGroup.setIsRequired(false);
        proteinGroup.setMaxSelections(2);
        proteinGroup.setIsActive(true);
        proteinGroup = modifierGroupRepository.save(proteinGroup);

        // Step 5: Create modifiers
        friesModifier = new Modifier();
        friesModifier.setModifierGroupId(sidesGroup.getModifierGroupId());
        friesModifier.setName("Fries");
        friesModifier.setPriceAdjustment(BigDecimal.ZERO);
        friesModifier.setIsActive(true);
        friesModifier = modifierRepository.save(friesModifier);

        addBaconModifier = new Modifier();
        addBaconModifier.setModifierGroupId(proteinGroup.getModifierGroupId());
        addBaconModifier.setName("Add Bacon");
        addBaconModifier.setPriceAdjustment(new BigDecimal("2.00"));
        addBaconModifier.setIsActive(true);
        addBaconModifier = modifierRepository.save(addBaconModifier);

        saladModifier = new Modifier();
        saladModifier.setModifierGroupId(sidesGroup.getModifierGroupId());
        saladModifier.setName("Salad");
        saladModifier.setPriceAdjustment(BigDecimal.ZERO);
        saladModifier.setIsActive(true);
        saladModifier = modifierRepository.save(saladModifier);

        addAvocadoModifier = new Modifier();
        addAvocadoModifier.setModifierGroupId(proteinGroup.getModifierGroupId());
        addAvocadoModifier.setName("Add Avocado");
        addAvocadoModifier.setPriceAdjustment(new BigDecimal("1.50"));
        addAvocadoModifier.setIsActive(true);
        addAvocadoModifier = modifierRepository.save(addAvocadoModifier);

        // Step 6: Create order items
        orderItem1 = new OrderItem();
        orderItem1.setOrderId(order1.getOrderId());
        orderItem1.setMenuItemId(menuItem1.getMenuItemId());
        orderItem1.setQuantity(1);
        orderItem1.setPrice(new BigDecimal("17.00"));
        orderItem1.setStatus("pending");
        orderItem1.setDelaySeconds(15);
        orderItem1.setDelayExpiresAt(LocalDateTime.now().plusSeconds(15));
        orderItem1.setIsLocked(false);
        orderItem1 = orderItemRepository.save(orderItem1);

        orderItem2 = new OrderItem();
        orderItem2.setOrderId(order1.getOrderId());
        orderItem2.setMenuItemId(menuItem2.getMenuItemId());
        orderItem2.setQuantity(1);
        orderItem2.setPrice(new BigDecimal("12.00"));
        orderItem2.setStatus("fired");
        orderItem2.setDelaySeconds(15);
        orderItem2.setDelayExpiresAt(LocalDateTime.now().minusSeconds(10));
        orderItem2.setIsLocked(true);
        orderItem2.setFiredAt(LocalDateTime.now().minusSeconds(5));
        orderItem2 = orderItemRepository.save(orderItem2);

        orderItem3 = new OrderItem();
        orderItem3.setOrderId(order2.getOrderId());
        orderItem3.setMenuItemId(menuItem3.getMenuItemId());
        orderItem3.setQuantity(1);
        orderItem3.setPrice(new BigDecimal("10.00"));
        orderItem3.setStatus("draft");
        orderItem3.setDelaySeconds(15);
        orderItem3.setIsLocked(false);
        orderItem3 = orderItemRepository.save(orderItem3);

        // Step 7: Finally create order item modifiers with valid foreign keys
        // Modifier: Order item 1 has Fries (no upcharge)
        modifier1 = new OrderItemModifier();
        modifier1.setOrderItemId(orderItem1.getOrderItemId());
        modifier1.setModifierId(friesModifier.getModifierId());
        modifier1.setPriceAdjustment(BigDecimal.ZERO);
        modifier1 = orderItemModifierRepository.save(modifier1);

        // Modifier: Order item 1 has Add Bacon (+$2.00)
        modifier2 = new OrderItemModifier();
        modifier2.setOrderItemId(orderItem1.getOrderItemId());
        modifier2.setModifierId(addBaconModifier.getModifierId());
        modifier2.setPriceAdjustment(new BigDecimal("2.00"));
        modifier2 = orderItemModifierRepository.save(modifier2);

        // Modifier: Order item 2 has Salad (no upcharge)
        modifier3 = new OrderItemModifier();
        modifier3.setOrderItemId(orderItem2.getOrderItemId());
        modifier3.setModifierId(saladModifier.getModifierId());
        modifier3.setPriceAdjustment(BigDecimal.ZERO);
        modifier3 = orderItemModifierRepository.save(modifier3);
    }

    @Test
    void testSave_ShouldPersistModifier() {
        // WHAT: Test saving a new order item modifier
        // WHY: Ensure basic create operation works
        
        // Given - New modifier selection
        OrderItemModifier newModifier = new OrderItemModifier();
        newModifier.setOrderItemId(orderItem3.getOrderItemId());
        newModifier.setModifierId(addAvocadoModifier.getModifierId());
        newModifier.setPriceAdjustment(new BigDecimal("1.50"));

        // When - Save to database
        OrderItemModifier saved = orderItemModifierRepository.save(newModifier);

        // Then - Should persist with generated ID
        assertNotNull(saved.getOrderItemModifierId());
        assertEquals(orderItem3.getOrderItemId(), saved.getOrderItemId());
        assertEquals(addAvocadoModifier.getModifierId(), saved.getModifierId());
    }

    @Test
    void testFindById_WhenExists_ShouldReturnModifier() {
        // WHAT: Test finding modifier by ID
        // WHY: Need to load specific modifier selections
        
        // Given - Modifier exists in database (from setUp)
        
        // When - Find by ID
        Optional<OrderItemModifier> result = orderItemModifierRepository.findById(modifier1.getOrderItemModifierId());

        // Then - Should find the modifier
        assertTrue(result.isPresent());
        assertEquals(orderItem1.getOrderItemId(), result.get().getOrderItemId());
        assertEquals(friesModifier.getModifierId(), result.get().getModifierId());
    }

    @Test
    void testFindById_WhenNotExists_ShouldReturnEmpty() {
        // WHAT: Test finding non-existent modifier
        // WHY: Handle missing modifiers gracefully
        
        // Given - Non-existent ID
        
        // When - Try to find
        Optional<OrderItemModifier> result = orderItemModifierRepository.findById(999L);

        // Then - Should return empty
        assertFalse(result.isPresent());
    }

    @Test
    void testFindAll_ShouldReturnAllModifiers() {
        // WHAT: Test retrieving all order item modifiers
        // WHY: Get complete history of all modifier selections
        
        // Given - 3 modifiers in database (from setUp)
        
        // When - Find all
        List<OrderItemModifier> modifiers = orderItemModifierRepository.findAll();

        // Then - Should get all 3 modifiers
        assertEquals(3, modifiers.size());
    }

    @Test
    void testFindByOrderItemId_ShouldReturnModifiersForItem() {
        // WHAT: Test finding all modifiers for an order item
        // WHY: Show what customizations customer selected for their Chicken Cutty
        
        // Given - Order item 1 has 2 modifiers (Fries + Add Bacon from setUp)
        
        // When - Find modifiers for order item 1
        List<OrderItemModifier> itemModifiers = orderItemModifierRepository.findByOrderItemId(orderItem1.getOrderItemId());

        // Then - Should get 2 modifiers
        assertEquals(2, itemModifiers.size());
        assertTrue(itemModifiers.stream().allMatch(m -> m.getOrderItemId().equals(orderItem1.getOrderItemId())));
    }

    @Test
    void testFindByModifierId_ShouldReturnOrderItemsWithModifier() {
        // WHAT: Test finding all order items that have a specific modifier
        // WHY: Track how many orders included "Add Bacon" (analytics)
        
        // Given - Add Bacon modifier is on 1 order item (from setUp)
        
        // When - Find order items with Add Bacon
        List<OrderItemModifier> itemsWithModifier = orderItemModifierRepository.findByModifierId(addBaconModifier.getModifierId());

        // Then - Should get 1 order item
        assertEquals(1, itemsWithModifier.size());
        assertEquals(addBaconModifier.getModifierId(), itemsWithModifier.get(0).getModifierId());
    }

    @Test
    void testDeleteById_ShouldRemoveModifier() {
        // WHAT: Test deleting an order item modifier
        // WHY: Remove modifier selections (not typical in production)
        
        // Given - Modifier exists
        Long modifierId = modifier3.getOrderItemModifierId();
        
        // When - Delete the modifier
        orderItemModifierRepository.deleteById(modifierId);

        // Then - Modifier should no longer exist
        Optional<OrderItemModifier> deleted = orderItemModifierRepository.findById(modifierId);
        assertFalse(deleted.isPresent());
    }

    @Test
    void testModifierWithPriceAdjustment_ShouldStoreUpcharge() {
        // WHAT: Test that price adjustments are stored correctly
        // WHY: Track upcharge for Add Bacon at time of order (+$2.00)
        
        // Given - Add Bacon modifier has price adjustment (from setUp)
        
        // When - Load Add Bacon modifier
        OrderItemModifier loaded = orderItemModifierRepository.findById(modifier2.getOrderItemModifierId()).orElseThrow();

        // Then - Price adjustment should be preserved
        assertEquals(new BigDecimal("2.00"), loaded.getPriceAdjustment());
        assertEquals(orderItem1.getOrderItemId(), loaded.getOrderItemId());
    }

    @Test
    void testModifierWithNoPriceAdjustment_ShouldStoreZero() {
        // WHAT: Test that free modifiers store zero price adjustment
        // WHY: Track Fries selection with no upcharge ($0.00)
        
        // Given - Fries modifier has no upcharge (from setUp)
        
        // When - Load Fries modifier
        OrderItemModifier loaded = orderItemModifierRepository.findById(modifier1.getOrderItemModifierId()).orElseThrow();

        // Then - Price adjustment should be zero
        assertEquals(BigDecimal.ZERO, loaded.getPriceAdjustment());
    }

    @Test
    void testMultipleModifiersForOneItem_ShouldAllowMultipleSelections() {
        // WHAT: Test that one order item can have multiple modifiers
        // WHY: Chicken Cutty can have both "Fries" and "Add Bacon"
        
        // Given - Order item 1 has 2 modifiers (from setUp)
        
        // When - Find all modifiers for order item 1
        List<OrderItemModifier> modifiers = orderItemModifierRepository.findByOrderItemId(orderItem1.getOrderItemId());

        // Then - Should have 2 different modifiers
        assertEquals(2, modifiers.size());
        assertEquals(friesModifier.getModifierId(), modifiers.get(0).getModifierId());
        assertEquals(addBaconModifier.getModifierId(), modifiers.get(1).getModifierId());
    }

    @Test
    void testHistoricalPricing_ShouldPreservePriceAtTimeOfOrder() {
        // WHAT: Test that price adjustments are captured at order time
        // WHY: Even if "Add Bacon" price changes later, order shows $2.00
        
        // Given - Modifier with price adjustment saved (from setUp)
        Long modifierId = modifier2.getOrderItemModifierId();
        
        // When - Load modifier later
        OrderItemModifier historical = orderItemModifierRepository.findById(modifierId).orElseThrow();

        // Then - Price adjustment is preserved from time of order
        assertEquals(new BigDecimal("2.00"), historical.getPriceAdjustment());
        // Even if current modifier price changes, this order's price stays $2.00
    }
}