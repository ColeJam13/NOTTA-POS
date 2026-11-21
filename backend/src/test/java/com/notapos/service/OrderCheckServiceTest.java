package com.notapos.service;

import com.notapos.dto.OrderCheckView;
import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for OrderCheckService.
 *
 * Tests the one place on the backend that adds up what a guest owes.
 *
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class OrderCheckServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    // Built by hand instead of @InjectMocks, because the tax rate is a plain value, not a mock
    private OrderCheckService orderCheckService;

    private Order testOrder;

    @BeforeEach
    void setUp() {
        orderCheckService = new OrderCheckService(orderRepository, orderItemRepository, new BigDecimal("0.03"));

        testOrder = new Order();
        testOrder.setOrderId(1L);
        testOrder.setTableId(1L);
        testOrder.setStatus("open");
    }

    // One order item with the given status, unit price, and quantity
    private OrderItem item(String status, String price, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrderId(1L);
        item.setStatus(status);
        item.setPrice(new BigDecimal(price));
        item.setQuantity(quantity);
        return item;
    }

    @Test
    void testBuildCheck_ShouldAddUpSentItemsAndTax() {
        // WHAT: Test the check for an order with two sent items
        // WHY: This is the amount the guest is charged

        // Given - $16.00 + $17.00, both already sent to the kitchen
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(
                item("pending", "16.00", 1),
                item("completed", "17.00", 1)));

        // When
        OrderCheckView check = orderCheckService.buildCheck(testOrder);

        // Then - 33.00 + 3% tax (0.99) = 33.99
        assertEquals(new BigDecimal("33.00"), check.subtotal());
        assertEquals(new BigDecimal("0.99"), check.tax());
        assertEquals(new BigDecimal("33.99"), check.total());
        assertEquals(new BigDecimal("0.03"), check.taxRate());
        assertEquals(2, check.itemCount());
        assertEquals(0, check.unsentItemCount());
        assertEquals(1L, check.orderId());
        assertEquals("open", check.status());
    }

    @Test
    void testBuildCheck_ShouldLeaveDraftsOffTheCheckAndCountThem() {
        // WHAT: Test an order with one sent item and two drafts
        // WHY: A draft never reached the kitchen, so it is not charged. It is counted so the
        //      screen can tell the server to send or remove it before closing the check.

        // Given
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(
                item("fired", "16.00", 1),
                item("draft", "17.00", 1),
                item("draft", "9.00", 1)));

        // When
        OrderCheckView check = orderCheckService.buildCheck(testOrder);

        // Then - only the $16.00 item is on the check
        assertEquals(new BigDecimal("16.00"), check.subtotal());
        assertEquals(1, check.itemCount());
        assertEquals(2, check.unsentItemCount());
    }

    @Test
    void testBuildCheck_ShouldCountItemsStillInTheEditWindow() {
        // WHAT: Test that an item in limbo is on the check
        // WHY: It has been sent; it is only waiting out its 15 seconds

        // Given
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(item("limbo", "12.50", 1)));

        // When
        OrderCheckView check = orderCheckService.buildCheck(testOrder);

        // Then
        assertEquals(new BigDecimal("12.50"), check.subtotal());
        assertEquals(1, check.itemCount());
        assertEquals(0, check.unsentItemCount());
    }

    @Test
    void testBuildCheck_ShouldMultiplyPriceByQuantity() {
        // WHAT: Test an item with a quantity above one
        // WHY: Price is for ONE item

        // Given - three coffees at $3.50
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(item("pending", "3.50", 3)));

        // When
        OrderCheckView check = orderCheckService.buildCheck(testOrder);

        // Then
        assertEquals(new BigDecimal("10.50"), check.subtotal());
    }

    @Test
    void testBuildCheck_ShouldRoundHalfACentOfTaxUp() {
        // WHAT: Test tax that lands exactly on half a cent
        // WHY: 16.50 x 3% = 0.495. Rounding must be predictable: half a cent goes up, to 0.50.

        // Given
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(item("pending", "16.50", 1)));

        // When
        OrderCheckView check = orderCheckService.buildCheck(testOrder);

        // Then
        assertEquals(new BigDecimal("0.50"), check.tax());
        assertEquals(new BigDecimal("17.00"), check.total());
    }

    @Test
    void testBuildCheck_WhenOrderHasNoItems_ShouldBeZero() {
        // WHAT: Test the check for an empty order
        // WHY: An empty order owes nothing (and cannot be paid - see PaymentService)

        // Given
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of());

        // When
        OrderCheckView check = orderCheckService.buildCheck(testOrder);

        // Then
        assertEquals(new BigDecimal("0.00"), check.subtotal());
        assertEquals(new BigDecimal("0.00"), check.tax());
        assertEquals(new BigDecimal("0.00"), check.total());
        assertEquals(0, check.itemCount());
    }

    @Test
    void testGetCheck_ShouldLoadTheOrderAndBuildItsCheck() {
        // WHAT: Test getting a check by order ID
        // WHY: This is what GET /api/orders/{id}/check calls

        // Given
        when(orderRepository.findById(1L)).thenReturn(Optional.of(testOrder));
        when(orderItemRepository.findByOrderId(1L)).thenReturn(List.of(item("pending", "16.00", 1)));

        // When
        OrderCheckView check = orderCheckService.getCheck(1L);

        // Then
        assertEquals(new BigDecimal("16.48"), check.total());
    }

    @Test
    void testGetCheck_WhenOrderNotFound_ShouldThrowException() {
        // WHAT: Test getting the check for an order that does not exist
        // WHY: Becomes a 404 for the screen

        // Given
        when(orderRepository.findById(99L)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> orderCheckService.getCheck(99L));
    }

    @Test
    void testBuildCheck_WithItemsAlreadyLoaded_ShouldRunNoQueries() {
        // WHAT: Test adding up a check from items the caller already has
        // WHY: A screen listing many checks loads all their items with one query and must
        //      not trigger another query per check (the N+1 problem)

        // When
        OrderCheckView check = orderCheckService.buildCheck(testOrder, List.of(
                item("completed", "16.00", 1),
                item("completed", "17.00", 1)));

        // Then
        assertEquals(new BigDecimal("33.99"), check.total());
        verifyNoInteractions(orderItemRepository);
    }

    @Test
    void testGetTaxRate_ShouldBeTheRateChecksAreWorkedOutWith() {
        // WHAT: Test the rate this service hands to anything that asks (GET /api/settings)
        // WHY: The order screen's running total and the check must use one number. The
        //      screen is given this value; the check below is worked out with it.

        // Given - a service set to 8.25%, and a $100.00 item
        OrderCheckService service = new OrderCheckService(orderRepository, orderItemRepository, new BigDecimal("0.0825"));

        // When
        OrderCheckView check = service.buildCheck(testOrder, List.of(item("pending", "100.00", 1)));

        // Then - the rate handed out is the rate on the check, and the tax is that rate applied
        assertEquals(new BigDecimal("0.0825"), service.getTaxRate());
        assertEquals(service.getTaxRate(), check.taxRate());
        assertEquals(new BigDecimal("8.25"), check.tax());
    }
}
