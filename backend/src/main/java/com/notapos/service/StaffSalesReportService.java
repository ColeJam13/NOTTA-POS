package com.notapos.service;

import com.notapos.dto.StaffSalesView;
import com.notapos.entity.Order;
import com.notapos.entity.Staff;
import com.notapos.enums.OrderItemStatus;
import com.notapos.enums.OrderStatus;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderItemRepository.ItemsSoldOnOrder;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.StaffRepository;
import com.notapos.service.ClosedCheckAmountsService.Amounts;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Adds up what each person sold in one time period: the numbers behind the leaderboard.
 *
 * WHAT: One row per person who opened at least one check that was closed in the period:
 *       checks closed, sales, check average, biggest single check, and items sold.
 * HOW:  At most four database queries, however many checks the period holds:
 *         1. the orders closed in the period            (the same query Financials runs)
 *         2. items, ONLY for orders with no recorded totals (ClosedCheckAmountsService)
 *         3. how many items each order sold, added up by the database (GROUP BY)
 *         4. the names of the people on those orders    (WHERE staff_id IN (...))
 * WHY:  The leaderboard used to be seven invented names typed into the screen. These
 *       are real, and they are the SAME sales the Financials report shows:
 *
 *         - a sale belongs to the period its check was CLOSED in;
 *         - it belongs to the person who OPENED the check (the order's staffId), the
 *           rule tips follow too;
 *         - what a check was worth comes from ClosedCheckAmountsService, the code the
 *           Financials report uses, not a copy of it.
 *
 *       So everyone's sales, plus the checks with nobody on record, is exactly
 *       Financials' revenue for the same period, to the cent. StaffSalesIntegrationTest
 *       holds the two reports to that.
 *
 * No payment is read here, so no tip can be in the answer (see StaffSalesView).
 *
 * @author CJ
 */
@Service
@Transactional(readOnly = true)      // All the queries see the same consistent snapshot of the data
public class StaffSalesReportService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final StaffRepository staffRepository;
    private final ClosedCheckAmountsService closedCheckAmountsService;

    @Autowired
    public StaffSalesReportService(OrderRepository orderRepository,
                                   OrderItemRepository orderItemRepository,
                                   StaffRepository staffRepository,
                                   ClosedCheckAmountsService closedCheckAmountsService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.staffRepository = staffRepository;
        this.closedCheckAmountsService = closedCheckAmountsService;
    }

    public StaffSalesView getReport(LocalDateTime from, LocalDateTime to) {     // What each person sold on checks closed between two moments (both included)
        // Query 1: the checks closed in the period. A check with nobody on record belongs to nobody.
        List<Order> closedOrders = orderRepository.findByStatusAndClosedAtBetween(OrderStatus.CLOSED.getValue(), from, to);
        List<Order> withAnOpener = closedOrders.stream().filter(order -> order.getStaffId() != null).toList();
        if (withAnOpener.isEmpty()) {
            return new StaffSalesView(List.of());                       // Nobody sold anything - skip the other queries
        }
        List<Long> orderIds = withAnOpener.stream().map(Order::getOrderId).toList();

        // Query 2 (only if a check has no recorded total): what each check was worth
        Map<Long, Amounts> amountsByOrderId = closedCheckAmountsService.amountsOf(withAnOpener);

        // Query 3: how many items each check sold. A check with no row here sold none.
        Map<Long, Long> itemsSoldByOrderId = orderItemRepository
                .countItemsSoldByOrderIdIn(orderIds, OrderItemStatus.DRAFT.getValue()).stream()
                .collect(Collectors.toMap(ItemsSoldOnOrder::getOrderId, ItemsSoldOnOrder::getItemsSold));

        // Add each check to the running sums of the person who opened it
        Map<Long, Sums> sumsByStaffId = new LinkedHashMap<>();
        for (Order order : withAnOpener) {
            Sums sums = sumsByStaffId.computeIfAbsent(order.getStaffId(), staffId -> new Sums());
            BigDecimal sale = amountsByOrderId.get(order.getOrderId()).subtotal();

            sums.checksClosed++;
            sums.sales = sums.sales.add(sale);
            sums.biggestCheck = sums.biggestCheck.max(sale);
            sums.itemsSold += itemsSoldByOrderId.getOrDefault(order.getOrderId(), 0L);
            sums.nameOnTheCheck = order.getServerName();
        }

        // Query 4: the people, for their names as they are today. findAllById also finds
        // someone who has since been deactivated: their sales in the period still happened.
        Map<Long, Staff> staffById = staffRepository.findAllById(sumsByStaffId.keySet()).stream()
                .collect(Collectors.toMap(Staff::getStaffId, Function.identity()));

        List<Unranked> people = new ArrayList<>();
        for (Map.Entry<Long, Sums> entry : sumsByStaffId.entrySet()) {
            Staff person = staffById.get(entry.getKey());
            // Staff are deactivated, never deleted, so the person is always found. If one ever
            // is not, the name written on their check is better than leaving their sales out.
            String name = person != null ? person.getName() : entry.getValue().nameOnTheCheck;
            people.add(new Unranked(entry.getKey(), name != null ? name : "Staff #" + entry.getKey(), entry.getValue()));
        }

        // Best sales first. People with equal sales are listed by name, then by ID, so the
        // list is in the same order every time it is asked for.
        people.sort(Comparator.comparing((Unranked person) -> person.sums().sales).reversed()
                .thenComparing(Unranked::name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Unranked::staffId));

        return new StaffSalesView(ranked(people));
    }

    /**
     * Gives each person their rank. Equal sales share a rank and the next one is skipped
     * (1, 1, 3): two people tied for first are both first, and the next person has two
     * people ahead of them, so they are third. Sports tables are ranked the same way.
     */
    private List<StaffSalesView.Row> ranked(List<Unranked> bestFirst) {
        List<StaffSalesView.Row> rows = new ArrayList<>();
        int rank = 0;
        BigDecimal salesAtThatRank = null;

        for (int position = 0; position < bestFirst.size(); position++) {
            Unranked person = bestFirst.get(position);
            Sums sums = person.sums();

            // compareTo, not equals: to BigDecimal, 20.0 and 20.00 are not "equal" but do compare as the same amount
            if (salesAtThatRank == null || sums.sales.compareTo(salesAtThatRank) != 0) {
                rank = position + 1;                                    // One more than the number of people ahead
                salesAtThatRank = sums.sales;
            }

            rows.add(new StaffSalesView.Row(
                    person.staffId(),
                    person.name(),
                    rank,
                    sums.checksClosed,
                    sums.sales.setScale(2, RoundingMode.HALF_UP),
                    sums.sales.divide(BigDecimal.valueOf(sums.checksClosed), 2, RoundingMode.HALF_UP),
                    sums.biggestCheck.setScale(2, RoundingMode.HALF_UP),
                    sums.itemsSold));
        }
        return rows;
    }

    // One person's running sums while the checks are gone through
    private static final class Sums {
        private int checksClosed = 0;
        private BigDecimal sales = BigDecimal.ZERO;
        private BigDecimal biggestCheck = BigDecimal.ZERO;
        private long itemsSold = 0;
        private String nameOnTheCheck;                                  // Only used if the person cannot be found (see above)
    }

    // A person and their sums, before the list is sorted and ranks are handed out
    private record Unranked(Long staffId, String name, Sums sums) {
    }
}
