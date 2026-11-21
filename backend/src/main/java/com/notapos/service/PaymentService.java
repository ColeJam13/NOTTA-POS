package com.notapos.service;
import org.springframework.transaction.annotation.Transactional;
import com.notapos.enums.OrderStatus;
import com.notapos.enums.TableStatus;
import com.notapos.exception.OrderStateException;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.dto.OrderCheckView;
import com.notapos.entity.Payment;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.PaymentRepository;
import com.notapos.repository.TableRepository;
import com.notapos.entity.Order;
import com.notapos.entity.RestaurantTable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Service layer for Payment operations.
 * 
 * Handles payment processing, tips, and split checks.
 * 
 * @author CJ
 */

@Service
@Transactional(readOnly = true)      // Every method runs in a database transaction; read-only unless a method says otherwise
public class PaymentService {
    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final TableRepository tableRepository;
    private final OrderCheckService orderCheckService;      // Works out what the order owes
    private final OrderItemService orderItemService;        // Ends the edit window when the check is closed
    
    @Autowired
    public PaymentService(PaymentRepository paymentRepository,
                         OrderRepository orderRepository,
                         TableRepository tableRepository,
                         OrderCheckService orderCheckService,
                         OrderItemService orderItemService) {
        this.paymentRepository = paymentRepository;
        this.orderRepository = orderRepository;
        this.tableRepository = tableRepository;
        this.orderCheckService = orderCheckService;
        this.orderItemService = orderItemService;
    }

    public List<Payment> getAllPayments() {                         // Get all payments
        return paymentRepository.findAll();
    }

    public Optional<Payment> getPaymentById(Long id) {              // Get payment by ID
        return paymentRepository.findById(id);
    }

    public List<Payment> getPaymentsByOrder(Long orderId) {         // Get payment by Order
        return paymentRepository.findByOrderId(orderId);
    }

    public List<Payment> getPaymentsByMethod(String paymentMethod) {    // Get payments by method (cash, card, etc.)
        return paymentRepository.findByPaymentMethod(paymentMethod);
    }

    public List<Payment> getPaymentsByStatus(String status) {           // Get payment by status
        return paymentRepository.findByStatus(status);
    }

    public List<Payment> getPaymentsBetween(LocalDateTime start, LocalDateTime end) {           // Get payments between certain times
        return paymentRepository.findPaymentsBetween(start, end);
    }

    public BigDecimal calculateTotalTips(LocalDateTime start, LocalDateTime end) {              // Get total tips between certain times
        BigDecimal total = paymentRepository.calculateTotalTips(start, end);
        return total != null ? total : BigDecimal.ZERO;
    }

    /**
     * Take payment for an order and close it.
     *
     * Everything here happens in one transaction: if any step fails, none of it
     * is saved, so there is never a payment without a closed order or the reverse.
     *
     * Before any money is recorded the order is checked:
     *   - it must exist (404)
     *   - it must still be open (409) - stops a check being paid twice
     *   - it must have no unsent items (409) - a draft was never made by the kitchen,
     *     so the server has to send it or remove it first
     *   - it must have something on it (409)
     *   - the amount must equal the check the backend works out (409) - if another
     *     screen added an item after this one loaded, the guest was shown an old total
     *
     * The order is held alone from the first line to the commit ("for update", see
     * OrderRepository). Those checks are only worth something if nothing changes
     * between making them and saving the payment:
     *   - a second payment for the same check waits here, and when its turn comes it
     *     reads the order as closed and is refused. (Before, two payments pressed at
     *     the same instant both read "open" and both were saved.)
     *   - an item being rung in, sent, or removed on another terminal either finishes
     *     before this starts, and is then part of the check that is added up below, or
     *     waits until this is done, and is then refused because the check is closed.
     */
    @Transactional
    public Payment createPayment(Payment payment) {
        Order order = orderRepository.findByIdLockedForUpdate(payment.getOrderId())
                .orElseThrow(() -> new ResourceNotFoundException("Order not found with id: " + payment.getOrderId()));

        if (!OrderStatus.OPEN.matches(order.getStatus())) {
            throw new OrderStateException("Order #" + order.getOrderId() + " is already closed.");
        }

        OrderCheckView check = orderCheckService.buildCheck(order);

        if (check.unsentItemCount() > 0) {
            throw new OrderStateException("This order has " + check.unsentItemCount()
                    + " unsent item(s). Send or remove them before closing the check.");
        }

        if (check.itemCount() == 0) {
            throw new OrderStateException("This order has no items to pay for.");
        }

        // compareTo, not equals: for BigDecimal, 33.9 and 33.90 are "equal" only with compareTo
        if (payment.getAmount().compareTo(check.total()) != 0) {
            throw new OrderStateException("The check is $" + check.total() + " but the payment was for $"
                    + payment.getAmount().setScale(2, RoundingMode.HALF_UP)
                    + ". The order changed - reopen it and try again.");
        }

        // Closing the check ends the edit window: anything still counting down goes to the kitchen now,
        // so an item cannot be removed from a check that has already been paid.
        orderItemService.sendOrderNow(order.getOrderId());

        // Save the payment. The amount is the backend's own number, to the cent.
        payment.setAmount(check.total());
        if (payment.getTipAmount() == null) {
            payment.setTipAmount(BigDecimal.ZERO);
        }
        Payment savedPayment = paymentRepository.save(payment);

        // Close the order, and record what the check came to
        order.setSubtotal(check.subtotal());
        order.setTax(check.tax());
        order.setTotal(check.total());
        order.setStatus(OrderStatus.CLOSED.getValue());
        order.setClosedAt(LocalDateTime.now());
        orderRepository.save(order);

        // Free up the table
        Optional<RestaurantTable> tableOpt = tableRepository.findById(order.getTableId());
        if (tableOpt.isPresent()) {
            RestaurantTable table = tableOpt.get();
            table.setStatus(TableStatus.AVAILABLE.getValue());
            tableRepository.save(table);
        }

        return savedPayment;
    }

    @Transactional
    public Payment updatePayment(Long id, Payment updatedPayment) {                                     // Update existing payment
        Payment existing = paymentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found with id: " + id));
        existing.setAmount(updatedPayment.getAmount());
        existing.setTipAmount(updatedPayment.getTipAmount());
        existing.setStatus(updatedPayment.getStatus());
        return paymentRepository.save(existing);
    }

    @Transactional
    public void deletePayment(Long id) {                                                        // Delete existing payment
        paymentRepository.deleteById(id);
    }
}