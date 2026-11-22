package com.notapos.service;

import com.notapos.dto.ClosedCheckView;
import com.notapos.dto.SearchResultsView;
import com.notapos.entity.MenuItem;
import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.entity.RestaurantTable;
import com.notapos.enums.SearchPeriod;
import com.notapos.enums.StaffRole;
import com.notapos.repository.MenuItemRepository;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.TableRepository;
import com.notapos.security.StaffSession;
import com.notapos.testsupport.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SearchService.
 *
 * Tests what the search box's one answer is built from, with the repositories
 * replaced by Mockito stand-ins: how much must be typed, what is handed to the
 * queries (and that it is handed over as a value), the cap on each group, the
 * period for closed checks, and that closed checks come from the one piece of
 * code that knows who may see a tip.
 *
 * The queries themselves are tested against a real database in
 * SearchQueriesRepositoryTest, and the whole thing through real requests in
 * SearchIntegrationTest.
 *
 * @author CJ
 */
@ExtendWith(MockitoExtension.class)
class SearchServiceTest {

    @Mock private TableRepository tableRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderItemRepository orderItemRepository;
    @Mock private MenuItemRepository menuItemRepository;
    @Mock private TableOverviewService tableOverviewService;

    private final MutableClock clock = new MutableClock();          // Stands still unless a test moves it
    private SearchService searchService;

    private static final StaffSession SARAH = new StaffSession(7L, "Sarah", StaffRole.SERVER, Instant.now(), false);

    @BeforeEach
    void setUp() {
        searchService = new SearchService(tableRepository, orderRepository, orderItemRepository,
                menuItemRepository, tableOverviewService, clock);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private RestaurantTable table(long id, String number) {
        RestaurantTable table = new RestaurantTable();
        table.setTableId(id);
        table.setTableNumber(number);
        table.setSection("Window");
        table.setSeatCount(4);
        table.setStatus("occupied");
        table.setIsQuickOrder(false);
        return table;
    }

    private Order order(long id, long tableId, String status) {
        Order order = new Order();
        order.setOrderId(id);
        order.setTableId(tableId);
        order.setStatus(status);
        order.setServerName("Sarah");
        order.setStaffId(7L);
        // The entity stamps its own opening time when it is saved and offers no setter;
        // a test that never saves it puts the time in directly
        ReflectionTestUtils.setField(order, "createdAt", LocalDateTime.of(2026, 11, 20, 12, 30));
        return order;
    }

    private OrderItem item(long orderId, String price, int quantity) {
        OrderItem item = new OrderItem();
        item.setOrderId(orderId);
        item.setMenuItemId(1L);
        item.setPrice(new BigDecimal(price));
        item.setQuantity(quantity);
        return item;
    }

    private MenuItem menuItem(long id, String name, boolean is86d) {
        MenuItem item = new MenuItem();
        item.setMenuItemId(id);
        item.setName(name);
        item.setCategory("Savory");
        item.setPrice(new BigDecimal("16.00"));
        item.setIs86d(is86d);
        item.setIsActive(true);
        return item;
    }

    // The pattern the queries were handed for what was typed
    private String patternFor(String typed) {
        searchService.search(typed, SearchPeriod.TODAY, SARAH);
        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(tableRepository).searchRegularTables(pattern.capture(), any());
        return pattern.getValue();
    }

    // ------------------------------------------------------------------
    // How much must be typed
    // ------------------------------------------------------------------

    @Test
    void testSearch_WithNothingOrOneLetter_ShouldAnswerNothingAndAskTheDatabaseNothing() {
        // WHAT: Nothing typed, only spaces, and a single letter
        // WHY: One letter matches half the menu. Words search from the second character.
        for (String typed : new String[] { null, "", "   ", "w", " w " }) {
            SearchResultsView results = searchService.search(typed, SearchPeriod.TODAY, SARAH);

            assertTrue(results.tables().results().isEmpty());
            assertTrue(results.openChecks().results().isEmpty());
            assertTrue(results.closedChecks().results().isEmpty());
            assertTrue(results.menuItems().results().isEmpty());
            assertFalse(results.tables().more());
        }
        verifyNoInteractions(tableRepository, orderRepository, orderItemRepository, menuItemRepository, tableOverviewService);
    }

    @Test
    void testSearch_WithOneDigit_ShouldSearch() {
        // WHAT: A single digit is enough
        // WHY: "7" is table 7. Digits search from the first character.
        searchService.search("7", SearchPeriod.TODAY, SARAH);

        verify(tableRepository).searchRegularTables(eq("%7%"), any());
    }

    @Test
    void testSearch_WithTwoLetters_ShouldSearch() {
        searchService.search("sa", SearchPeriod.TODAY, SARAH);

        verify(menuItemRepository).searchOnTheMenu(eq("%sa%"), any());
    }

    // ------------------------------------------------------------------
    // What the queries are handed
    // ------------------------------------------------------------------

    @Test
    void testSearch_ShouldLookForTheTextInLowerCaseWithoutTheSpacesAroundIt() {
        // WHY: "W7" and "w7" are the same table to the person typing
        assertEquals("%w7%", patternFor("  W7 "));
    }

    @Test
    void testSearch_WithAPercentSign_ShouldLookForAPercentSignNotForEverything() {
        // WHAT: Typing "50%"
        // WHY: In a LIKE comparison % means "any run of characters". Passed through as
        //      it is, "%" would match every table, check, and menu item in the database.
        assertEquals("%50\\%%", patternFor("50%"));
    }

    @Test
    void testSearch_WithAnUnderscoreOrABackslash_ShouldLookForThatCharacter() {
        // WHY: _ means "any one character" to LIKE, and \ is the escape character itself
        assertEquals("%a\\_b\\\\c%", patternFor("a_b\\c"));
    }

    @Test
    void testSearch_WithSqlTypedIn_ShouldHandItOverAsTextToLookFor() {
        // WHAT: A classic attempt to change the query from the search box
        // WHY: What is typed is a VALUE handed to the query, never part of its text.
        //      Here that means: it arrives at the repository whole, as a pattern.
        String typed = "'; DROP TABLE orders; --";

        assertEquals("%'; drop table orders; --%", patternFor(typed));
    }

    @Test
    void testSearch_WithDigits_ShouldAlsoLookForThatCheckNumber() {
        // WHY: "12" is table 12, and it is check number 12
        searchService.search("12", SearchPeriod.TODAY, SARAH);

        verify(orderRepository).searchWithStatus(eq("open"), eq("%12%"), eq(12L), any());
        verify(orderRepository).searchWithStatusClosedSince(eq("closed"), any(), eq("%12%"), eq(12L), any());
    }

    @Test
    void testSearch_WithLettersInIt_ShouldNotLookForACheckNumber() {
        searchService.search("w12", SearchPeriod.TODAY, SARAH);

        verify(orderRepository).searchWithStatus(eq("open"), eq("%w12%"), isNull(), any());
    }

    @Test
    void testSearch_WithMoreDigitsThanACheckNumberCanHave_ShouldNotFail() {
        // WHAT: Thirty digits
        // WHY: It cannot be read as a number at all; it must simply find nothing
        assertDoesNotThrow(() -> searchService.search("123456789012345678901234567890", SearchPeriod.TODAY, SARAH));

        verify(orderRepository).searchWithStatus(eq("open"), anyString(), isNull(), any());
    }

    @Test
    void testSearch_WithVeryLongText_ShouldSearchForItsFirstFiftyCharacters() {
        String typed = "x".repeat(200);

        assertEquals("%" + "x".repeat(SearchService.MAX_QUERY_LENGTH) + "%", patternFor(typed));
    }

    // ------------------------------------------------------------------
    // The cap
    // ------------------------------------------------------------------

    @Test
    void testSearch_ShouldAskForOneMoreThanTheCap() {
        // WHY: One extra row is how "there are more" is told from "exactly ten"
        searchService.search("w1", SearchPeriod.TODAY, SARAH);

        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(tableRepository).searchRegularTables(anyString(), page.capture());
        assertEquals(SearchService.GROUP_LIMIT + 1, page.getValue().getPageSize());
        assertEquals(0, page.getValue().getPageNumber());
    }

    @Test
    void testSearch_WithMoreMatchesThanTheCap_ShouldReturnTheCapAndSayThereAreMore() {
        List<RestaurantTable> eleven = LongStream.rangeClosed(1, 11).mapToObj(n -> table(n, "W" + n)).toList();
        when(tableRepository.searchRegularTables(anyString(), any())).thenReturn(eleven);

        SearchResultsView results = searchService.search("w1", SearchPeriod.TODAY, SARAH);

        assertEquals(10, results.tables().results().size());
        assertEquals("W1", results.tables().results().get(0).tableNumber());
        assertEquals("W10", results.tables().results().get(9).tableNumber());
        assertTrue(results.tables().more());
    }

    @Test
    void testSearch_WithExactlyTheCap_ShouldNotSayThereAreMore() {
        List<RestaurantTable> ten = LongStream.rangeClosed(1, 10).mapToObj(n -> table(n, "W" + n)).toList();
        when(tableRepository.searchRegularTables(anyString(), any())).thenReturn(ten);

        SearchResultsView results = searchService.search("w1", SearchPeriod.TODAY, SARAH);

        assertEquals(10, results.tables().results().size());
        assertFalse(results.tables().more());
    }

    // ------------------------------------------------------------------
    // Closed checks: the period, and who builds them
    // ------------------------------------------------------------------

    // Midnight at the start of today, by the same calendar the service uses
    private LocalDateTime startOfToday() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault()).toLocalDate().atStartOfDay();
    }

    private LocalDateTime sinceFor(SearchPeriod period) {
        clearInvocations(orderRepository);
        searchService.search("w1", period, SARAH);
        ArgumentCaptor<LocalDateTime> since = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(orderRepository).searchWithStatusClosedSince(eq("closed"), since.capture(), anyString(), any(), any());
        return since.getValue();
    }

    @Test
    void testSearch_ShouldLookForClosedChecksFromTheStartOfTheDayThePeriodNames() {
        // WHAT: Each of the four periods
        // WHY: "Today" is since midnight by the backend's own calendar, the clock checks
        //      are stamped with. "Yesterday" is since the midnight before, and so on.
        assertEquals(startOfToday(), sinceFor(SearchPeriod.TODAY));
        assertEquals(startOfToday().minusDays(1), sinceFor(SearchPeriod.YESTERDAY));
        assertEquals(startOfToday().minusDays(6), sinceFor(SearchPeriod.LAST_7_DAYS));
        assertEquals(startOfToday().minusDays(29), sinceFor(SearchPeriod.LAST_30_DAYS));
    }

    @Test
    void testSearch_ShouldBuildClosedChecksWithTheTablesScreensOwnCode_ForThePersonAsking() {
        // WHAT: Two closed checks are found
        // WHY: Who may see a tip is decided in ONE place (TableOverviewService). Search
        //      must hand it the checks and the person asking, and pass on what comes
        //      back: it must not build its own view of a closed check.
        Order first = order(21L, 1L, "closed");
        Order second = order(22L, 2L, "closed");
        when(orderRepository.searchWithStatusClosedSince(any(), any(), any(), any(), any())).thenReturn(List.of(first, second));
        when(tableRepository.findAllById(anyIterable())).thenReturn(List.of(table(1L, "W1"), table(2L, "W2")));
        when(orderItemRepository.findByOrderIdIn(anyCollection())).thenReturn(List.of(item(21L, "10.00", 1)));
        ClosedCheckView view = new ClosedCheckView(21L, 1L, "W1", false, "Sarah", null, null,
                new BigDecimal("10.00"), new BigDecimal("0.30"), new BigDecimal("10.30"), null, null, List.of());
        when(tableOverviewService.buildClosedChecks(eq(List.of(first, second)), any(), any(), eq(SARAH))).thenReturn(List.of(view));

        SearchResultsView results = searchService.search("sarah", SearchPeriod.TODAY, SARAH);

        assertEquals(List.of(view), results.closedChecks().results());
        verify(tableOverviewService).buildClosedChecks(eq(List.of(first, second)), any(), any(), eq(SARAH));
    }

    // ------------------------------------------------------------------
    // What a result shows
    // ------------------------------------------------------------------

    @Test
    void testSearch_ShouldDescribeAnOpenCheckWithItsTableItsItemsAndItsSubtotal() {
        // WHY: Choosing the result opens the order screen for that table, which needs the
        //      table's own fields; the row shows how much is on the check.
        Order open = order(31L, 5L, "open");
        when(orderRepository.searchWithStatus(any(), any(), any(), any())).thenReturn(List.of(open));
        RestaurantTable w5 = table(5L, "W5");
        when(tableRepository.findAllById(anyIterable())).thenReturn(List.of(w5));
        when(orderItemRepository.findByOrderIdIn(anyCollection())).thenReturn(List.of(item(31L, "18.00", 1), item(31L, "12.50", 2)));

        SearchResultsView results = searchService.search("w5", SearchPeriod.TODAY, SARAH);

        var check = results.openChecks().results().get(0);
        assertEquals(31L, check.orderId());
        assertEquals(5L, check.tableId());
        assertEquals("W5", check.tableNumber());
        assertEquals("occupied", check.status());
        assertFalse(check.isQuickOrder());
        assertEquals("Sarah", check.serverName());
        assertEquals(2, check.itemCount());
        assertEquals(0, new BigDecimal("43.00").compareTo(check.subtotal()));      // 18.00 + 2 x 12.50
        assertEquals(LocalDateTime.of(2026, 11, 20, 12, 30), check.openedAt());
    }

    @Test
    void testSearch_ShouldDescribeATableAndAMenuItem() {
        when(tableRepository.searchRegularTables(anyString(), any())).thenReturn(List.of(table(7L, "W7")));
        when(menuItemRepository.searchOnTheMenu(anyString(), any())).thenReturn(List.of(menuItem(3L, "Garden Toast", true)));

        SearchResultsView results = searchService.search("w7", SearchPeriod.TODAY, SARAH);

        var table = results.tables().results().get(0);
        assertEquals(7L, table.tableId());
        assertEquals("W7", table.tableNumber());
        assertEquals("Window", table.section());
        assertEquals(4, table.seatCount());
        assertEquals("occupied", table.status());
        assertFalse(table.isQuickOrder());

        var item = results.menuItems().results().get(0);
        assertEquals(3L, item.menuItemId());
        assertEquals("Garden Toast", item.name());
        assertEquals("Savory", item.category());
        assertEquals(0, new BigDecimal("16.00").compareTo(item.price()));
        assertTrue(item.is86d());
    }

    @Test
    void testSearch_WhenNoCheckMatches_ShouldNotAskForTablesOrItems() {
        // WHY: Queries 5 and 6 are only for the checks that were found
        when(tableRepository.searchRegularTables(anyString(), any())).thenReturn(new ArrayList<>(List.of(table(7L, "W7"))));

        searchService.search("w7", SearchPeriod.TODAY, SARAH);

        verify(tableRepository, never()).findAllById(anyIterable());
        verify(orderItemRepository, never()).findByOrderIdIn(anyCollection());
    }
}
