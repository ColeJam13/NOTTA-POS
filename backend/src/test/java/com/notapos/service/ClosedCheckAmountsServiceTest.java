package com.notapos.service;

import com.notapos.dto.OrderCheckView;
import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.repository.OrderItemRepository;
import com.notapos.service.ClosedCheckAmountsService.Amounts;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ClosedCheckAmountsService.
 *
 * Tests the one answer to "what was this closed check worth?" that the Financials
 * report and the per-person sales report both use.
 *
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class ClosedCheckAmountsServiceTest {

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private OrderCheckService orderCheckService;

    @InjectMocks
    private ClosedCheckAmountsService closedCheckAmountsService;

    private Order closedOrder(Long id, String subtotal, String tax, String total) {
        Order order = new Order();
        order.setOrderId(id);
        order.setStatus("closed");
        order.setSubtotal(new BigDecimal(subtotal));
        order.setTax(new BigDecimal(tax));
        order.setTotal(new BigDecimal(total));
        return order;
    }

    private OrderItem itemOn(Long orderId) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setQuantity(1);
        item.setPrice(new BigDecimal("16.00"));
        item.setStatus("completed");
        return item;
    }

    @Test
    void testAmountsOf_WhenTheCheckWasRecorded_ShouldUseWhatIsOnTheOrder_AndLoadNoItems() {
        // WHAT: Test a check paid since the backend started recording amounts
        // WHY: Its subtotal, tax, and total are what the guest was charged. Reading the
        //      items again for every check in a month would be thousands of rows for nothing.

        // Given
        Order paid = closedOrder(1L, "33.00", "0.99", "33.99");

        // When
        Map<Long, Amounts> amounts = closedCheckAmountsService.amountsOf(List.of(paid));

        // Then
        assertEquals(new Amounts(new BigDecimal("33.00"), new BigDecimal("0.99"), new BigDecimal("33.99")), amounts.get(1L));
        verify(orderItemRepository, never()).findByOrderIdIn(anyCollection());
        verifyNoInteractions(orderCheckService);
    }

    @Test
    void testAmountsOf_WhenTheCheckHoldsZeros_ShouldWorkItOutFromItsItems() {
        // WHAT: Test a check paid before amounts were recorded: it holds 0.00 three times
        // WHY: Without this an old check counts as a sale of nothing, in every report

        // Given
        Order old = closedOrder(2L, "0.00", "0.00", "0.00");
        OrderItem item = itemOn(2L);
        when(orderItemRepository.findByOrderIdIn(List.of(2L))).thenReturn(List.of(item));
        when(orderCheckService.buildCheck(old, List.of(item))).thenReturn(new OrderCheckView(
                2L, "closed", 1, 0, new BigDecimal("16.00"), new BigDecimal("0.48"), new BigDecimal("16.48"), new BigDecimal("0.03")));

        // When
        Map<Long, Amounts> amounts = closedCheckAmountsService.amountsOf(List.of(old));

        // Then
        assertEquals(new Amounts(new BigDecimal("16.00"), new BigDecimal("0.48"), new BigDecimal("16.48")), amounts.get(2L));
    }

    @Test
    void testAmountsOf_WithBothKinds_ShouldAskForItemsOfTheOldChecksOnly_InOneQuery() {
        // WHAT: Test a period holding recorded checks and old ones
        // WHY: One query for all the old checks' items, and none for the recorded ones.
        //      An old check with no items left at all is worth nothing, not an error.

        // Given
        Order paid = closedOrder(1L, "33.00", "0.99", "33.99");
        Order old = closedOrder(2L, "0.00", "0.00", "0.00");
        Order oldAndEmpty = closedOrder(3L, "0.00", "0.00", "0.00");
        OrderItem item = itemOn(2L);
        when(orderItemRepository.findByOrderIdIn(List.of(2L, 3L))).thenReturn(List.of(item));
        when(orderCheckService.buildCheck(old, List.of(item))).thenReturn(new OrderCheckView(
                2L, "closed", 1, 0, new BigDecimal("16.00"), new BigDecimal("0.48"), new BigDecimal("16.48"), new BigDecimal("0.03")));
        when(orderCheckService.buildCheck(oldAndEmpty, List.of())).thenReturn(new OrderCheckView(
                3L, "closed", 0, 0, new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.00"), new BigDecimal("0.03")));

        // When
        Map<Long, Amounts> amounts = closedCheckAmountsService.amountsOf(List.of(paid, old, oldAndEmpty));

        // Then - every check has an answer, in the order given
        assertEquals(List.of(1L, 2L, 3L), new ArrayList<>(amounts.keySet()));
        assertEquals(new BigDecimal("33.00"), amounts.get(1L).subtotal());
        assertEquals(new BigDecimal("16.00"), amounts.get(2L).subtotal());
        assertEquals(new BigDecimal("0.00"), amounts.get(3L).subtotal());
        verify(orderItemRepository, times(1)).findByOrderIdIn(anyCollection());
    }

    @Test
    void testAmountsOf_WithNoChecks_ShouldAnswerNothing_AndAskNothing() {
        // WHAT: Test an empty list
        // WHY: A period with nothing closed must not send "WHERE order_id IN ()" to the database

        assertTrue(closedCheckAmountsService.amountsOf(List.of()).isEmpty());
        verifyNoInteractions(orderItemRepository);
    }
}
