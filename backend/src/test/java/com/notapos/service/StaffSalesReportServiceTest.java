package com.notapos.service;

import com.notapos.dto.OrderCheckView;
import com.notapos.dto.StaffSalesView;
import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.entity.Staff;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderItemRepository.ItemsSoldOnOrder;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.StaffRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StaffSalesReportService.
 *
 * Tests the numbers behind the leaderboard: what each person sold in a period.
 *
 * @author CJ
 */

@ExtendWith(MockitoExtension.class)
class StaffSalesReportServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderItemRepository orderItemRepository;

    @Mock
    private StaffRepository staffRepository;

    @Mock
    private OrderCheckService orderCheckService;

    private StaffSalesReportService staffSalesReportService;

    private final LocalDateTime from = LocalDateTime.of(2026, 11, 18, 0, 0);
    private final LocalDateTime to = LocalDateTime.of(2026, 11, 18, 23, 59, 59);

    private long nextOrderId = 1;

    // The report asks ClosedCheckAmountsService what each check was worth. The real one is
    // used here, over the same faked repositories, so these tests cover the whole sum.
    @BeforeEach
    void setUp() {
        staffSalesReportService = new StaffSalesReportService(orderRepository, orderItemRepository, staffRepository,
                new ClosedCheckAmountsService(orderItemRepository, orderCheckService));
    }

    // A closed check with its amounts recorded (3% tax), opened by the given person
    private Order check(Long staffId, String nameOnTheCheck, String subtotal) {
        BigDecimal sale = new BigDecimal(subtotal);
        BigDecimal tax = sale.multiply(new BigDecimal("0.03")).setScale(2, java.math.RoundingMode.HALF_UP);
        Order order = new Order();
        order.setOrderId(nextOrderId++);
        order.setTableId(1L);
        order.setStatus("closed");
        order.setStaffId(staffId);
        order.setServerName(nameOnTheCheck);
        order.setSubtotal(sale);
        order.setTax(tax);
        order.setTotal(sale.add(tax));
        order.setClosedAt(LocalDateTime.of(2026, 11, 18, 12, 0));
        return order;
    }

    private Staff person(Long id, String name, boolean active) {
        Staff staff = new Staff();
        staff.setStaffId(id);
        staff.setName(name);
        staff.setIsActive(active);
        return staff;
    }

    // One row of the items-sold query: "this order sold this many"
    private ItemsSoldOnOrder sold(Order order, long items) {
        return new ItemsSoldOnOrder() {
            @Override public Long getOrderId() { return order.getOrderId(); }
            @Override public Long getItemsSold() { return items; }
        };
    }

    private void closedInThePeriod(Order... orders) {
        when(orderRepository.findByStatusAndClosedAtBetween("closed", from, to)).thenReturn(List.of(orders));
    }

    private void onStaff(Staff... people) {
        when(staffRepository.findAllById(anyIterable())).thenReturn(List.of(people));
    }

    @Test
    void testGetReport_ShouldAddUpEachPersonsChecks() {
        // WHAT: Test the five numbers for two people
        // WHY: These are what the leaderboard shows, and what challenges will be measured by

        // Given - Sarah closed three checks, Dan one
        Order s1 = check(7L, "Sarah", "30.00"), s2 = check(7L, "Sarah", "50.00"), s3 = check(7L, "Sarah", "10.50");
        Order d1 = check(8L, "Dan", "45.25");
        closedInThePeriod(s1, d1, s2, s3);
        when(orderItemRepository.countItemsSoldByOrderIdIn(anyCollection(), anyString()))
                .thenReturn(List.of(sold(s1, 2), sold(s2, 4), sold(s3, 1), sold(d1, 3)));
        onStaff(person(7L, "Sarah", true), person(8L, "Dan", true));

        // When
        StaffSalesView report = staffSalesReportService.getReport(from, to);

        // Then - best sales first
        assertEquals(2, report.staff().size());

        StaffSalesView.Row sarah = report.staff().get(0);
        assertEquals(7L, sarah.staffId());
        assertEquals("Sarah", sarah.name());
        assertEquals(1, sarah.rank());
        assertEquals(3, sarah.checksClosed());
        assertEquals(new BigDecimal("90.50"), sarah.sales());
        assertEquals(new BigDecimal("30.17"), sarah.checkAverage());        // 90.50 / 3 = 30.1666..., rounded to the cent
        assertEquals(new BigDecimal("50.00"), sarah.biggestCheck());
        assertEquals(7, sarah.itemsSold());

        StaffSalesView.Row dan = report.staff().get(1);
        assertEquals(8L, dan.staffId());
        assertEquals(2, dan.rank());
        assertEquals(1, dan.checksClosed());
        assertEquals(new BigDecimal("45.25"), dan.sales());
        assertEquals(new BigDecimal("45.25"), dan.checkAverage());
        assertEquals(new BigDecimal("45.25"), dan.biggestCheck());
        assertEquals(3, dan.itemsSold());
    }

    @Test
    void testGetReport_SalesAreBeforeTax_TheFigureFinancialsCallsRevenue() {
        // WHAT: Test which of a check's three amounts counts as the sale
        // WHY: Financials' revenue is the subtotal. If this report counted the total with
        //      tax, the two screens would disagree by the tax on every check.

        // Given - one check: 100.00 + 3.00 tax
        closedInThePeriod(check(7L, "Sarah", "100.00"));
        onStaff(person(7L, "Sarah", true));

        // When
        StaffSalesView.Row sarah = staffSalesReportService.getReport(from, to).staff().get(0);

        // Then
        assertEquals(new BigDecimal("100.00"), sarah.sales());
        assertEquals(new BigDecimal("100.00"), sarah.biggestCheck());
    }

    @Test
    void testGetReport_EqualSalesShareARank_AndTheNextRankIsSkipped() {
        // WHAT: Test ranks when two people sold exactly the same
        // WHY: Two people tied for first are both first. The next person has two people
        //      ahead of them, so they are third, not second.

        // Given - Avery and Blake both sold 100.00 (Blake as two checks), Casey 60.00, Drew 60.00, Eli 5.00
        closedInThePeriod(
                check(3L, "Casey", "60.00"), check(2L, "Blake", "40.00"), check(1L, "Avery", "100.00"),
                check(2L, "Blake", "60.00"), check(5L, "Eli", "5.00"), check(4L, "Drew", "60.00"));
        onStaff(person(1L, "Avery", true), person(2L, "Blake", true), person(3L, "Casey", true),
                person(4L, "Drew", true), person(5L, "Eli", true));

        // When
        List<StaffSalesView.Row> rows = staffSalesReportService.getReport(from, to).staff();

        // Then - people who are tied are listed by name
        assertEquals(List.of("Avery", "Blake", "Casey", "Drew", "Eli"), rows.stream().map(StaffSalesView.Row::name).toList());
        assertEquals(List.of(1, 1, 3, 3, 5), rows.stream().map(StaffSalesView.Row::rank).toList());
    }

    @Test
    void testGetReport_AmountsWrittenDifferently_ShouldStillTie() {
        // WHAT: Test a tie between 20.0 and 20.00
        // WHY: To BigDecimal those are not "equal" (they differ in how many decimals they
        //      carry) but they are the same money. Ranking must compare the amount.

        // Given
        Order one = check(1L, "Avery", "20.00");
        Order other = check(2L, "Blake", "20.00");
        other.setSubtotal(new BigDecimal("20.0"));
        closedInThePeriod(one, other);
        onStaff(person(1L, "Avery", true), person(2L, "Blake", true));

        // When
        List<StaffSalesView.Row> rows = staffSalesReportService.getReport(from, to).staff();

        // Then - and both are written to the cent
        assertEquals(List.of(1, 1), rows.stream().map(StaffSalesView.Row::rank).toList());
        assertEquals(new BigDecimal("20.00"), rows.get(1).sales());
    }

    @Test
    void testGetReport_ACheckWithNobodyOnRecord_ShouldBeInNoRow() {
        // WHAT: Test a check closed before staff profiles existed (no staffId)
        // WHY: It belongs to nobody. It must not become a row named "null", and it must
        //      not be given to whoever happens to share the name printed on it.

        // Given
        Order nobodys = check(null, "Sarah", "500.00");
        Order sarahs = check(7L, "Sarah", "30.00");
        closedInThePeriod(nobodys, sarahs);
        onStaff(person(7L, "Sarah", true));

        // When
        List<StaffSalesView.Row> rows = staffSalesReportService.getReport(from, to).staff();

        // Then - one row, with her own check only
        assertEquals(1, rows.size());
        assertEquals(new BigDecimal("30.00"), rows.get(0).sales());
        assertEquals(1, rows.get(0).checksClosed());
    }

    @Test
    void testGetReport_WhenOnlyNobodysChecksWereClosed_ShouldBeEmpty_AndAskNothingElse() {
        // WHAT: Test a period in which every closed check has nobody on record
        // WHY: "No sales yet for this period" is the honest answer for the leaderboard

        // Given
        closedInThePeriod(check(null, "CJ", "40.00"));

        // When
        StaffSalesView report = staffSalesReportService.getReport(from, to);

        // Then
        assertTrue(report.staff().isEmpty());
        verifyNoInteractions(orderItemRepository, staffRepository);
    }

    @Test
    void testGetReport_WhenNothingWasClosed_ShouldBeEmpty_AndAskNothingElse() {
        // WHAT: Test a period with no closed checks at all
        // WHY: An empty list, not an error, and no query with an empty IN (...)

        // Given
        closedInThePeriod();

        // When
        StaffSalesView report = staffSalesReportService.getReport(from, to);

        // Then
        assertEquals(List.of(), report.staff());
        verifyNoInteractions(orderItemRepository, staffRepository);
    }

    @Test
    void testGetReport_SomeoneSinceDeactivated_ShouldStillAppear() {
        // WHAT: Test a person who was deactivated after closing checks in the period
        // WHY: Their sales happened. Leaving them out would also make everyone's sales
        //      add up to less than the Financials report shows.

        // Given
        closedInThePeriod(check(7L, "Sarah", "30.00"));
        onStaff(person(7L, "Sarah", false));

        // When
        List<StaffSalesView.Row> rows = staffSalesReportService.getReport(from, to).staff();

        // Then
        assertEquals(1, rows.size());
        assertEquals("Sarah", rows.get(0).name());
    }

    @Test
    void testGetReport_ShouldUseThePersonsNameAsItIsToday_NotTheOneOnTheCheck() {
        // WHAT: Test a person whose name was changed on the Staff screen during the period
        // WHY: One person is one row. Their checks carry whichever name they had when each
        //      was opened; grouping is by staff ID and the row shows today's name.

        // Given - two checks, opened under two spellings
        closedInThePeriod(check(7L, "Sara", "30.00"), check(7L, "Sarah Miller", "20.00"));
        onStaff(person(7L, "Sarah Miller", true));

        // When
        List<StaffSalesView.Row> rows = staffSalesReportService.getReport(from, to).staff();

        // Then
        assertEquals(1, rows.size());
        assertEquals("Sarah Miller", rows.get(0).name());
        assertEquals(new BigDecimal("50.00"), rows.get(0).sales());
        verify(staffRepository).findAllById(Set.of(7L));
    }

    @Test
    void testGetReport_WhenThePersonCannotBeFound_ShouldFallBackToTheNameOnTheirCheck() {
        // WHAT: Test a staff ID with no staff row behind it
        // WHY: Staff are deactivated, never deleted, so this should not happen. If it ever
        //      does, the sales still count and the row still says who it was.

        // Given
        closedInThePeriod(check(99L, "Dan", "30.00"));
        onStaff();

        // When
        List<StaffSalesView.Row> rows = staffSalesReportService.getReport(from, to).staff();

        // Then
        assertEquals(1, rows.size());
        assertEquals(99L, rows.get(0).staffId());
        assertEquals("Dan", rows.get(0).name());
    }

    @Test
    void testGetReport_ACheckWithNoRecordedTotals_ShouldBeWorkedOutFromItsItems() {
        // WHAT: Test a check paid before the backend recorded amounts (it holds zeros)
        // WHY: Financials works such a check out from its items. This report must too,
        //      through the same code, or the two would disagree by exactly these checks.

        // Given
        Order old = check(7L, "Sarah", "0.00");
        OrderItem item = new OrderItem();
        item.setOrderId(old.getOrderId());
        closedInThePeriod(old);
        when(orderItemRepository.findByOrderIdIn(List.of(old.getOrderId()))).thenReturn(List.of(item));
        when(orderCheckService.buildCheck(old, List.of(item))).thenReturn(new OrderCheckView(
                old.getOrderId(), "closed", 1, 0, new BigDecimal("16.00"), new BigDecimal("0.48"), new BigDecimal("16.48"), new BigDecimal("0.03")));
        onStaff(person(7L, "Sarah", true));

        // When
        StaffSalesView.Row sarah = staffSalesReportService.getReport(from, to).staff().get(0);

        // Then
        assertEquals(new BigDecimal("16.00"), sarah.sales());
        assertEquals(new BigDecimal("16.00"), sarah.biggestCheck());
    }

    @Test
    void testGetReport_ItemsSold_ShouldLeaveDraftsOut_AndCountACheckWithNoneAsZero() {
        // WHAT: Test how items are counted
        // WHY: A draft was never sent, so it was never sold: the query is told to leave
        //      drafts out. A check the query has no row for sold nothing; that is zero,
        //      not a crash.

        // Given - two checks, and the database only has items for the first
        Order withItems = check(7L, "Sarah", "30.00");
        Order withNone = check(7L, "Sarah", "10.00");
        closedInThePeriod(withItems, withNone);
        when(orderItemRepository.countItemsSoldByOrderIdIn(List.of(withItems.getOrderId(), withNone.getOrderId()), "draft"))
                .thenReturn(List.of(sold(withItems, 3)));
        onStaff(person(7L, "Sarah", true));

        // When
        StaffSalesView.Row sarah = staffSalesReportService.getReport(from, to).staff().get(0);

        // Then
        assertEquals(3, sarah.itemsSold());
        verify(orderItemRepository).countItemsSoldByOrderIdIn(List.of(withItems.getOrderId(), withNone.getOrderId()), "draft");
    }

    @Test
    void testGetReport_ShouldAskForTheChecksClosedInThePeriod_TheWayFinancialsDoes() {
        // WHAT: Test which checks the report is built from
        // WHY: One counting rule for both reports: a sale belongs to the period its check
        //      was CLOSED in. Same query, same status, same two moments.

        // Given
        closedInThePeriod();

        // When
        staffSalesReportService.getReport(from, to);

        // Then
        verify(orderRepository).findByStatusAndClosedAtBetween("closed", from, to);
        verifyNoMoreInteractions(orderRepository);
    }
}
