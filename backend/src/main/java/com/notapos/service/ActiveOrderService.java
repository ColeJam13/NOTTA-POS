package com.notapos.service;

import com.notapos.dto.ActiveOrderItemView;
import com.notapos.dto.ActiveOrderView;
import com.notapos.dto.OrderItemModifierView;
import com.notapos.entity.MenuItem;
import com.notapos.entity.Order;
import com.notapos.entity.OrderItem;
import com.notapos.entity.RestaurantTable;
import com.notapos.enums.OrderStatus;
import com.notapos.repository.MenuItemRepository;
import com.notapos.repository.OrderItemRepository;
import com.notapos.repository.OrderRepository;
import com.notapos.repository.TableRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Builds the "active orders" view: every open order with its table and items.
 *
 * WHAT: Answers the Active Orders screen with ONE response.
 * HOW:  At most six database queries no matter how many orders are open:
 *         1. all open orders
 *         2. all items for those orders        (WHERE order_id IN (...))
 *         3. the menu items those items use    (WHERE menu_item_id IN (...))
 *         4. the tables those orders sit at    (WHERE table_id IN (...))
 *         5. the modifiers chosen for those items   (WHERE order_item_id IN (...))
 *         6. the names of those modifiers           (WHERE modifier_id IN (...))
 *       The results are then matched up in memory using maps.
 *       (Queries 5 and 6 live in OrderItemModifierService.)
 * WHY:  The slow alternative is the "N+1 problem": 1 query for the orders,
 *       then 1 more query per order for its items. 30 open orders = 31 queries.
 *
 * @author CJ
 */
@Service
@Transactional(readOnly = true)      // All the queries see the same consistent snapshot of the data
public class ActiveOrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final TableRepository tableRepository;
    private final OrderItemModifierService orderItemModifierService;

    @Autowired
    public ActiveOrderService(OrderRepository orderRepository,
                              OrderItemRepository orderItemRepository,
                              MenuItemRepository menuItemRepository,
                              TableRepository tableRepository,
                              OrderItemModifierService orderItemModifierService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.tableRepository = tableRepository;
        this.orderItemModifierService = orderItemModifierService;
    }

    public List<ActiveOrderView> getActiveOrders() {                    // Get every open order with its table and items
        // Query 1: open orders
        List<Order> openOrders = orderRepository.findByStatus(OrderStatus.OPEN.getValue());
        if (openOrders.isEmpty()) {
            return List.of();                                           // Nothing open - skip the other queries
        }

        // Query 2: every item on those orders, grouped by the order it belongs to
        List<Long> orderIds = openOrders.stream().map(Order::getOrderId).toList();
        List<OrderItem> allItems = orderItemRepository.findByOrderIdIn(orderIds);
        Map<Long, List<OrderItem>> itemsByOrderId = allItems.stream()
                .collect(Collectors.groupingBy(OrderItem::getOrderId));

        // Query 3: menu items, keyed by ID so each name lookup is instant
        Set<Long> menuItemIds = allItems.stream().map(OrderItem::getMenuItemId).collect(Collectors.toSet());
        Map<Long, MenuItem> menuItemsById = menuItemRepository.findAllById(menuItemIds).stream()
                .collect(Collectors.toMap(MenuItem::getMenuItemId, Function.identity()));

        // Query 4: tables, keyed by ID
        Set<Long> tableIds = openOrders.stream().map(Order::getTableId).collect(Collectors.toSet());
        Map<Long, RestaurantTable> tablesById = tableRepository.findAllById(tableIds).stream()
                .collect(Collectors.toMap(RestaurantTable::getTableId, Function.identity()));

        // Queries 5 and 6: the modifiers chosen for every item, with names, keyed by order item ID
        List<Long> orderItemIds = allItems.stream().map(OrderItem::getOrderItemId).toList();
        Map<Long, List<OrderItemModifierView>> modifiersByOrderItemId =
                orderItemModifierService.getModifierViewsForOrderItems(orderItemIds);

        // Match everything up - oldest order first so cards do not jump around on screen
        return openOrders.stream()
                .sorted(Comparator.comparing(Order::getOrderId))
                .map(order -> toView(
                        order,
                        tablesById.get(order.getTableId()),
                        itemsByOrderId.getOrDefault(order.getOrderId(), List.of()),
                        menuItemsById,
                        modifiersByOrderItemId))
                .toList();
    }

    private ActiveOrderView toView(Order order,
                                   RestaurantTable table,
                                   List<OrderItem> items,
                                   Map<Long, MenuItem> menuItemsById,
                                   Map<Long, List<OrderItemModifierView>> modifiersByOrderItemId) {
        // Items sorted by ID so a line keeps its position when its status changes
        List<ActiveOrderItemView> itemViews = items.stream()
                .sorted(Comparator.comparing(OrderItem::getOrderItemId))
                .map(item -> {
                    MenuItem menuItem = menuItemsById.get(item.getMenuItemId());
                    return new ActiveOrderItemView(
                            item.getOrderItemId(),
                            item.getMenuItemId(),
                            menuItem != null ? menuItem.getName() : "Item " + item.getMenuItemId(),
                            item.getQuantity(),
                            item.getPrice(),
                            item.getStatus(),
                            item.getSpecialInstructions(),
                            modifiersByOrderItemId.getOrDefault(item.getOrderItemId(), List.of()));
                })
                .toList();

        return new ActiveOrderView(
                order.getOrderId(),
                order.getTableId(),
                table != null ? table.getTableNumber() : null,
                table != null ? table.getIsQuickOrder() : null,
                order.getServerName(),
                order.getCreatedAt(),
                itemViews);
    }
}
