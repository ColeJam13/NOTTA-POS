package com.notapos.repository;

import com.notapos.entity.MenuItem;
import com.notapos.entity.Order;
import com.notapos.entity.RestaurantTable;
import com.notapos.service.SearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for the three queries behind the search box
 * (TableRepository.searchRegularTables, OrderRepository.searchWithStatus and
 * searchWithStatusClosedSince, MenuItemRepository.searchOnTheMenu).
 *
 * Runs them against a real PostgreSQL 16, because what is being tested is what
 * the DATABASE does with the pattern: upper and lower case, the two special
 * characters of LIKE, the order of the results, and text that looks like SQL.
 *
 * @author CJ
 */
class SearchQueriesRepositoryTest extends BaseRepositoryTest {

    @Autowired private TableRepository tableRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private MenuItemRepository menuItemRepository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private static final Pageable TEN = PageRequest.of(0, 10);

    private RestaurantTable w7;
    private RestaurantTable w17;
    private RestaurantTable quickOrder;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        menuItemRepository.deleteAll();
        tableRepository.deleteAll();

        w7 = table("W7", false);
        w17 = table("W17", false);
        table("A1", false);
        quickOrder = table("QO7", true);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private RestaurantTable table(String number, Boolean quickOrder) {
        RestaurantTable table = new RestaurantTable();
        table.setTableNumber(number);
        table.setSeatCount(4);
        table.setStatus("available");
        table.setIsQuickOrder(quickOrder);
        return tableRepository.save(table);
    }

    private Order order(RestaurantTable table, String serverName, String status, LocalDateTime closedAt) {
        Order order = new Order();
        order.setTableId(table.getTableId());
        order.setServerName(serverName);
        order.setStatus(status);
        order.setClosedAt(closedAt);
        return orderRepository.saveAndFlush(order);
    }

    private MenuItem menuItem(String name, boolean active, boolean is86d) {
        MenuItem item = new MenuItem();
        item.setName(name);
        item.setCategory("Savory");
        item.setPrice(new BigDecimal("10.00"));
        item.setIsActive(active);
        item.setIs86d(is86d);
        return menuItemRepository.save(item);
    }

    // The pattern the service would build for what was typed
    private String typed(String text) {
        return "%" + text.toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
    }

    private List<String> numbersOf(List<RestaurantTable> tables) {
        return tables.stream().map(RestaurantTable::getTableNumber).toList();
    }

    // ------------------------------------------------------------------
    // Tables
    // ------------------------------------------------------------------

    @Test
    void testSearchRegularTables_ShouldFindByPartOfTheNumber_ShortestFirst_AndNotQuickOrders() {
        // WHAT: Typing "7"
        // WHY: W7 is the likelier meaning than W17, so it comes first. QO7 is a Quick
        //      Order, which is a check and not a place on the floor.
        assertEquals(List.of("W7", "W17"), numbersOf(tableRepository.searchRegularTables(typed("7"), TEN)));
    }

    @Test
    void testSearchRegularTables_ShouldNotCareAboutUpperOrLowerCase() {
        assertEquals(List.of("W7", "W17"), numbersOf(tableRepository.searchRegularTables(typed("w"), TEN)));
        assertEquals(List.of("W7"), numbersOf(tableRepository.searchRegularTables(typed("W7"), TEN)));
    }

    @Test
    void testSearchRegularTables_ShouldCountATableWithNoQuickOrderFlagAsRegular() {
        // WHY: Rows inserted by older seed files never set the flag (see findRegularTables)
        jdbcTemplate.update("INSERT INTO tables (table_number, seat_count, status, is_quick_order, created_at) "
                + "VALUES ('N7', 2, 'available', NULL, now())");

        assertTrue(numbersOf(tableRepository.searchRegularTables(typed("n7"), TEN)).contains("N7"));
    }

    @Test
    void testSearchRegularTables_ShouldReturnNoMoreThanThePageAsksFor() {
        assertEquals(1, tableRepository.searchRegularTables(typed("w"), PageRequest.of(0, 1)).size());
    }

    @Test
    void testSearchRegularTables_WithAPercentSignOrUnderscore_ShouldMatchOnlyThatCharacter() {
        // WHAT: Typing "%" or "_" finds nothing here, because no table number contains one
        // WHY: Unescaped, "%" would match every table and "_" every table with a number
        assertTrue(tableRepository.searchRegularTables(typed("%"), TEN).isEmpty());
        assertTrue(tableRepository.searchRegularTables(typed("_"), TEN).isEmpty());

        table("B_2", false);
        assertEquals(List.of("B_2"), numbersOf(tableRepository.searchRegularTables(typed("_"), TEN)));
    }

    @Test
    void testSearchRegularTables_WithSqlTypedIn_ShouldFindNothingAndChangeNothing() {
        // WHAT: Text that would end the query and delete a table if it were joined into it
        // WHY: It is a parameter. The database is told "look for this text", and the
        //      tables are all still there afterwards.
        long before = tableRepository.count();

        assertTrue(tableRepository.searchRegularTables(typed("'; DELETE FROM tables; --"), TEN).isEmpty());
        assertTrue(tableRepository.searchRegularTables(typed("' OR '1'='1"), TEN).isEmpty());

        assertEquals(before, tableRepository.count());
    }

    // ------------------------------------------------------------------
    // Checks
    // ------------------------------------------------------------------

    @Test
    void testSearchWithStatus_ShouldFindAnOpenCheckByItsTableNumber() {
        Order onW7 = order(w7, "Sarah", "open", null);
        order(w17, "Dan", "closed", LocalDateTime.now());

        List<Order> found = orderRepository.searchWithStatus("open", typed("w7"), null, TEN);

        assertEquals(List.of(onW7.getOrderId()), found.stream().map(Order::getOrderId).toList());
    }

    @Test
    void testSearchWithStatus_ShouldFindAnOpenCheckByWhoOpenedIt_InAnyCase() {
        Order sarahs = order(w7, "Sarah Miller", "open", null);
        order(w17, "Dan", "open", null);

        List<Order> found = orderRepository.searchWithStatus("open", typed("MILL"), null, TEN);

        assertEquals(List.of(sarahs.getOrderId()), found.stream().map(Order::getOrderId).toList());
    }

    @Test
    void testSearchWithStatus_ShouldFindAnOpenCheckByItsNumber() {
        // WHAT: The check's own number is given, and matches nothing else about it
        Order onW7 = order(w7, "Sarah", "open", null);
        order(w17, "Dan", "open", null);

        List<Order> found = orderRepository.searchWithStatus("open", typed("no such text"), onW7.getOrderId(), TEN);

        assertEquals(List.of(onW7.getOrderId()), found.stream().map(Order::getOrderId).toList());
    }

    @Test
    void testSearchWithStatus_ShouldFindAQuickOrdersCheckByItsNumber() {
        // WHY: A Quick Order is not listed as a table, but its check is a check
        Order quick = order(quickOrder, "Sarah", "open", null);

        List<Order> found = orderRepository.searchWithStatus("open", typed("qo7"), null, TEN);

        assertEquals(List.of(quick.getOrderId()), found.stream().map(Order::getOrderId).toList());
    }

    @Test
    void testSearchWithStatus_WithACheckWhoseOpenerIsNotRecorded_ShouldStillFindItByTable() {
        order(w7, null, "open", null);

        assertEquals(1, orderRepository.searchWithStatus("open", typed("w7"), null, TEN).size());
        assertTrue(orderRepository.searchWithStatus("open", typed("sarah"), null, TEN).isEmpty());
    }

    @Test
    void testSearchWithStatus_ShouldListTheNewestFirst() {
        Order older = order(w7, "Sarah", "closed", LocalDateTime.now());
        Order newer = order(w7, "Sarah", "closed", LocalDateTime.now());

        List<Order> found = orderRepository.searchWithStatus("closed", typed("w7"), null, TEN);

        assertEquals(List.of(newer.getOrderId(), older.getOrderId()), found.stream().map(Order::getOrderId).toList());
    }

    @Test
    void testSearchWithStatusClosedSince_ShouldFindOnlyChecksClosedAtOrAfterThatMoment_MostRecentFirst() {
        // WHAT: Three paid checks on W7: one before the moment, one exactly at it, one after
        LocalDateTime midnight = LocalDateTime.of(2026, 11, 20, 0, 0);
        order(w7, "Sarah", "closed", midnight.minusSeconds(1));
        Order atMidnight = order(w7, "Sarah", "closed", midnight);
        Order atNoon = order(w7, "Sarah", "closed", midnight.plusHours(12));
        order(w7, "Sarah", "open", null);                                 // Still open: not a closed check at all

        List<Order> found = orderRepository.searchWithStatusClosedSince("closed", midnight, typed("w7"), null, TEN);

        assertEquals(List.of(atNoon.getOrderId(), atMidnight.getOrderId()), found.stream().map(Order::getOrderId).toList());
    }

    @Test
    void testSearchWithStatusClosedSince_ShouldFindByNumberAndByOpener() {
        LocalDateTime since = LocalDateTime.of(2026, 11, 20, 0, 0);
        Order dans = order(w17, "Dan", "closed", since.plusHours(1));
        order(w7, "Sarah", "closed", since.plusHours(2));

        assertEquals(List.of(dans.getOrderId()), orderRepository
                .searchWithStatusClosedSince("closed", since, typed("dan"), null, TEN).stream().map(Order::getOrderId).toList());
        assertEquals(List.of(dans.getOrderId()), orderRepository
                .searchWithStatusClosedSince("closed", since, typed("no such text"), dans.getOrderId(), TEN).stream().map(Order::getOrderId).toList());
    }

    @Test
    void testSearchWithStatus_WithSqlTypedIn_ShouldFindNothingAndChangeNothing() {
        order(w7, "Sarah", "open", null);
        long before = orderRepository.count();

        assertTrue(orderRepository.searchWithStatus("open", typed("'; DROP TABLE orders; --"), null, TEN).isEmpty());
        assertTrue(orderRepository.searchWithStatus("open", typed("%"), null, TEN).isEmpty());

        assertEquals(before, orderRepository.count());
    }

    // ------------------------------------------------------------------
    // The menu
    // ------------------------------------------------------------------

    @Test
    void testSearchOnTheMenu_ShouldFindByPartOfTheName_InAnyCase_ByName() {
        menuItem("Garden Toast", true, false);
        menuItem("Classic French Toast", true, false);
        menuItem("Omelette", true, false);

        List<MenuItem> found = menuItemRepository.searchOnTheMenu(typed("TOAST"), TEN);

        assertEquals(List.of("Classic French Toast", "Garden Toast"), found.stream().map(MenuItem::getName).toList());
    }

    @Test
    void testSearchOnTheMenu_ShouldFindAnEightySixedItem_AndNotOneTakenOffTheMenu() {
        // WHY: An 86'd item is still on the menu (the answer says it is out). An inactive
        //      one is not on the menu at all.
        menuItem("Garden Toast", true, true);
        menuItem("Old Toast Special", false, false);

        List<MenuItem> found = menuItemRepository.searchOnTheMenu(typed("toast"), TEN);

        assertEquals(List.of("Garden Toast"), found.stream().map(MenuItem::getName).toList());
        assertTrue(found.get(0).getIs86d());
    }

    @Test
    void testSearchOnTheMenu_WithAPercentSign_ShouldFindOnlyNamesThatHaveOne() {
        menuItem("Garden Toast", true, false);
        menuItem("100% Rye", true, false);

        assertEquals(List.of("100% Rye"), menuItemRepository.searchOnTheMenu(typed("%"), TEN).stream().map(MenuItem::getName).toList());
    }

    @Test
    void testTheServicesPattern_ShouldBeTheOneTheseTestsUse() {
        // WHY: These tests build the pattern themselves; this ties them to the real one
        for (String text : new String[] { "W7", "50%", "a_b", "back\\slash", "'; DROP TABLE orders; --" }) {
            assertEquals(typed(text), SearchService.patternFor(text));
        }
    }
}
