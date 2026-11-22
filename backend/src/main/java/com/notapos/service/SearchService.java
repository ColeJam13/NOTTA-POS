package com.notapos.service;

import com.notapos.dto.ClosedCheckView;
import com.notapos.dto.SearchGroupView;
import com.notapos.dto.SearchMenuItemView;
import com.notapos.dto.SearchOpenCheckView;
import com.notapos.dto.SearchResultsView;
import com.notapos.dto.SearchTableView;
import com.notapos.entity.MenuItem;
import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.entity.RestaurantTable;
import com.notapos.enums.OrderStatus;
import com.notapos.enums.SearchPeriod;
import com.notapos.repository.MenuItemRepository;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.TableRepository;
import com.notapos.security.StaffSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The search box in the top bar: finds tables, open checks, closed checks, and
 * menu items from a few typed characters.
 *
 * WHAT: One answer with four groups, each capped at GROUP_LIMIT.
 * HOW:  At most ten database queries however much is in the database:
 *         1. tables whose number matches
 *         2. open checks that match          (table number, opener's name, check number)
 *         3. closed checks that match, for the period asked for
 *         4. menu items whose name matches
 *         5. the tables those checks are on  (WHERE table_id IN (...))
 *         6. every item on those checks      (WHERE order_id IN (...))
 *         7 to 10. what a closed check shows (payments, menu items, modifiers and their
 *                  names): TableOverviewService's queries 5 to 8, reused, not copied
 * WHY:  Getting to a table or a check without walking through screens, and
 *       finding a paid check older than the 50 the Tables screen lists.
 *
 * WHAT IS TYPED NEVER BECOMES PART OF A QUERY'S TEXT. It is handed to the
 * repository methods as a parameter (see OrderRepository), which the database
 * receives separately from the query. Typing a quote, a semicolon, or SQL does
 * nothing but fail to match.
 *
 * TIPS: a closed check here is the same ClosedCheckView the Tables screen shows,
 * built by the same code, so the same rule holds (the owner's decision D10): the
 * tip and payment method are in the answer only for the person who opened the
 * check, and for managers.
 *
 * HOW MUCH MUST BE TYPED: digits search from the first one ("7" finds table W7
 * and check 7). Anything else searches from the second character, because one
 * letter matches half the menu.
 *
 * @author CJ
 */
@Service
@Transactional(readOnly = true)      // All the queries see the same consistent snapshot of the data
public class SearchService {

    // How many results a group holds at most
    public static final int GROUP_LIMIT = 10;

    // Only this much of what was typed is searched for. No table number, name, or menu
    // item is longer, and the search box does not accept more.
    public static final int MAX_QUERY_LENGTH = 50;

    // One more than the cap is asked for, so that "there are more" can be told from
    // "exactly the cap" without counting every match
    private static final Pageable ONE_MORE_THAN_THE_CAP = PageRequest.of(0, GROUP_LIMIT + 1);

    private final TableRepository tableRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final TableOverviewService tableOverviewService;
    private final Clock clock;

    @Autowired
    public SearchService(TableRepository tableRepository,
                         OrderRepository orderRepository,
                         OrderItemRepository orderItemRepository,
                         MenuItemRepository menuItemRepository,
                         TableOverviewService tableOverviewService,
                         Clock clock) {
        this.tableRepository = tableRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.tableOverviewService = tableOverviewService;
        this.clock = clock;
    }

    /**
     * Search for what was typed.
     *
     * @param typed  The text from the search box, as typed
     * @param period How far back to look for closed checks
     * @param viewer The signed-in person asking. Decides which tips are in the answer.
     */
    public SearchResultsView search(String typed, SearchPeriod period, StaffSession viewer) {
        String query = cleaned(typed);
        if (!longEnough(query)) {
            return SearchResultsView.nothing();                         // Too little to search for: no queries at all
        }
        String pattern = patternFor(query);
        Long orderNumber = asNumber(query);

        // Queries 1 to 4: what matches, one more than the cap of each
        List<RestaurantTable> tables = tableRepository.searchRegularTables(pattern, ONE_MORE_THAN_THE_CAP);
        List<Order> openOrders = orderRepository.searchWithStatus(
                OrderStatus.OPEN.getValue(), pattern, orderNumber, ONE_MORE_THAN_THE_CAP);
        List<Order> closedOrders = orderRepository.searchWithStatusClosedSince(
                OrderStatus.CLOSED.getValue(), startOf(period), pattern, orderNumber, ONE_MORE_THAN_THE_CAP);
        List<MenuItem> menuItems = menuItemRepository.searchOnTheMenu(pattern, ONE_MORE_THAN_THE_CAP);

        List<Order> openShown = capped(openOrders);
        List<Order> closedShown = capped(closedOrders);

        // Queries 5 and 6: the tables those checks are on, and their items, each in one query
        List<Order> checksShown = new ArrayList<>(openShown);
        checksShown.addAll(closedShown);
        Map<Long, RestaurantTable> tablesById = tablesOf(checksShown);
        Map<Long, List<OrderItem>> itemsByOrderId = itemsOf(checksShown);

        return new SearchResultsView(
                new SearchGroupView<>(capped(tables).stream().map(this::toTableView).toList(), overTheCap(tables)),
                new SearchGroupView<>(openShown.stream()
                        .map(order -> toOpenCheckView(order, tablesById.get(order.getTableId()),
                                itemsByOrderId.getOrDefault(order.getOrderId(), List.of())))
                        .toList(), overTheCap(openOrders)),
                // Queries 7 to 10, and the rule about tips, live in TableOverviewService
                new SearchGroupView<>(closedChecks(closedShown, tablesById, itemsByOrderId, viewer), overTheCap(closedOrders)),
                new SearchGroupView<>(capped(menuItems).stream().map(this::toMenuItemView).toList(), overTheCap(menuItems)));
    }

    // ------------------------------------------------------------------
    // What was typed
    // ------------------------------------------------------------------

    // The typed text without the spaces around it, and no longer than is ever needed
    private static String cleaned(String typed) {
        String text = typed == null ? "" : typed.strip();
        return text.length() > MAX_QUERY_LENGTH ? text.substring(0, MAX_QUERY_LENGTH) : text;
    }

    // Digits search from the first character; anything else from the second
    static boolean longEnough(String query) {
        if (query.isEmpty()) {
            return false;
        }
        return allDigits(query) || query.length() >= 2;
    }

    private static boolean allDigits(String query) {
        return query.chars().allMatch(character -> character >= '0' && character <= '9');
    }

    /**
     * The pattern that means "contains this text", for a LIKE comparison.
     *
     * LIKE has two special characters: % stands for any run of characters and _ for
     * any single one. The pattern is the text with a % at each end. If the PERSON
     * typed a % or an _, it must be looked for as itself, so each is given a
     * backslash in front (the queries say ESCAPE '\'), and so is a backslash.
     * Without this, typing % would "match" every row.
     *
     * Lower-case, because the queries compare against LOWER(column): "w7" finds W7.
     */
    public static String patternFor(String query) {
        String escaped = query.toLowerCase()
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    // The typed text as a check number, or null when it is not a number (or is one too
    // long to be a check number at all)
    static Long asNumber(String query) {
        if (!allDigits(query) || query.length() > 18) {
            return null;
        }
        return Long.valueOf(query);
    }

    // The first moment of the period: midnight, by the backend's own calendar, the same
    // clock closed checks are stamped with. (The Clock gives a moment that is the same
    // everywhere; the time zone turns it into "what day is it here".)
    private LocalDateTime startOf(SearchPeriod period) {
        return LocalDateTime.ofInstant(clock.instant(), ZoneId.systemDefault())
                .toLocalDate()
                .minusDays(period.getDaysBeforeToday())
                .atStartOfDay();
    }

    // ------------------------------------------------------------------
    // The cap
    // ------------------------------------------------------------------

    private static <T> List<T> capped(List<T> found) {
        return found.size() > GROUP_LIMIT ? found.subList(0, GROUP_LIMIT) : found;
    }

    private static boolean overTheCap(List<?> found) {
        return found.size() > GROUP_LIMIT;
    }

    // ------------------------------------------------------------------
    // What each result shows
    // ------------------------------------------------------------------

    private Map<Long, RestaurantTable> tablesOf(List<Order> orders) {
        Set<Long> tableIds = orders.stream().map(Order::getTableId).collect(Collectors.toSet());
        if (tableIds.isEmpty()) {
            return Map.of();                                            // No checks found - skip the query
        }
        return tableRepository.findAllById(tableIds).stream()
                .collect(Collectors.toMap(RestaurantTable::getTableId, Function.identity()));
    }

    private Map<Long, List<OrderItem>> itemsOf(List<Order> orders) {
        List<Long> orderIds = orders.stream().map(Order::getOrderId).toList();
        if (orderIds.isEmpty()) {
            return Map.of();
        }
        return orderItemRepository.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));
    }

    private List<ClosedCheckView> closedChecks(List<Order> closedOrders,
                                               Map<Long, RestaurantTable> tablesById,
                                               Map<Long, List<OrderItem>> itemsByOrderId,
                                               StaffSession viewer) {
        return tableOverviewService.buildClosedChecks(closedOrders, tablesById, itemsByOrderId, viewer);
    }

    private SearchTableView toTableView(RestaurantTable table) {
        return new SearchTableView(
                table.getTableId(),
                table.getTableNumber(),
                table.getSection(),
                table.getSeatCount(),
                table.getStatus(),
                Boolean.TRUE.equals(table.getIsQuickOrder()));
    }

    private SearchOpenCheckView toOpenCheckView(Order order, RestaurantTable table, List<OrderItem> items) {
        BigDecimal subtotal = items.stream()
                .map(item -> item.getPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SearchOpenCheckView(
                order.getOrderId(),
                order.getTableId(),
                table.getTableNumber(),
                table.getStatus(),
                Boolean.TRUE.equals(table.getIsQuickOrder()),
                order.getServerName(),
                items.size(),
                subtotal,
                order.getCreatedAt());
    }

    private SearchMenuItemView toMenuItemView(MenuItem item) {
        return new SearchMenuItemView(
                item.getMenuItemId(),
                item.getName(),
                item.getCategory(),
                item.getPrice(),
                Boolean.TRUE.equals(item.getIs86d()));
    }
}
