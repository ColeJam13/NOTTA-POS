package com.notapos.service;

import org.springframework.transaction.annotation.Transactional;
import com.notapos.dto.ModifierGroupView;
import com.notapos.dto.ModifierOptionView;
import com.notapos.dto.OrderItemModifierView;
import com.notapos.dto.OrderItemModifiersRequest;
import com.notapos.dto.OrderItemRequest;
import com.notapos.entity.MenuItem;
import com.notapos.entity.Modifier;
import com.notapos.entity.OrderItem;
import com.notapos.entity.OrderItemModifier;
import com.notapos.enums.OrderItemStatus;
import com.notapos.exception.InvalidModifierSelectionException;
import com.notapos.exception.ItemLockedException;
import com.notapos.exception.ItemUnavailableException;
import com.notapos.exception.ResourceNotFoundException;
import com.notapos.repository.MenuItemRepository;
import com.notapos.repository.ModifierRepository;
import com.notapos.repository.OrderItemModifierRepository;
import com.notapos.repository.OrderItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Service layer for OrderItemModifier operations.
 * 
 * Contains business logic for managing order item modifiers.
 * 
 * Besides plain create/read/delete, this is where an item is rung in
 * TOGETHER with its modifiers and kitchen note (one transaction, so the
 * kitchen can never receive an item that is missing its modifiers), where
 * the chosen modifiers are checked against the menu's rules, and where the
 * upcharges are added to the item's price.
 * 
 * @author CJ
 */

@Service
@Transactional(readOnly = true)      // Every method runs in a database transaction; read-only unless a method says otherwise
public class OrderItemModifierService {
    private final OrderItemModifierRepository orderItemModifierRepository;
    private final OrderItemRepository orderItemRepository;
    private final MenuItemRepository menuItemRepository;
    private final ModifierRepository modifierRepository;
    private final MenuModifierService menuModifierService;
    private final OrderItemService orderItemService;            // Restarts the order's edit window when an item changes

    @Autowired
    public OrderItemModifierService(OrderItemModifierRepository orderItemModifierRepository,
                                    OrderItemRepository orderItemRepository,
                                    MenuItemRepository menuItemRepository,
                                    ModifierRepository modifierRepository,
                                    MenuModifierService menuModifierService,
                                    OrderItemService orderItemService) {
        this.orderItemModifierRepository = orderItemModifierRepository;
        this.orderItemRepository = orderItemRepository;
        this.menuItemRepository = menuItemRepository;
        this.modifierRepository = modifierRepository;
        this.menuModifierService = menuModifierService;
        this.orderItemService = orderItemService;
    }

    public List<OrderItemModifier> getAllOrderItemModifiers() {                                     // Get all order item modifiers
        return orderItemModifierRepository.findAll();
    }

    public Optional<OrderItemModifier> getOrderItemModifierById(Long id) {                          // Get order item modifier by ID
        return orderItemModifierRepository.findById(id);
    }

    public List<OrderItemModifier> getModifiersForOrderItem(Long orderItemId) {                     // Get all modifiers for an order item
        return orderItemModifierRepository.findByOrderItemId(orderItemId);
    }

    public List<OrderItemModifier> getOrderItemsWithModifier(Long modifierId) {                     // Get all order items that used a specific modifier
        return orderItemModifierRepository.findByModifierId(modifierId);
    }

    @Transactional
    public OrderItemModifier createOrderItemModifier(OrderItemModifier orderItemModifier) {         // Create a new order item modifier
        return orderItemModifierRepository.save(orderItemModifier);
    }

    @Transactional
    public void deleteOrderItemModifier(Long id) {                                                  // Delete an order item modifier
        orderItemModifierRepository.deleteById(id);
    }

    // ------------------------------------------------------------------------
    // Reading modifiers for the screen (names already filled in)
    // ------------------------------------------------------------------------

    /**
     * Get the chosen modifiers for MANY order items at once, with names.
     *
     * Two queries no matter how many items are asked for (the modifier rows,
     * then the names of the modifiers they point to) - see "N+1" in
     * ActiveOrderService for why that matters.
     *
     * @param orderItemIds The order items to look up
     * @return A map from order item ID to that item's modifiers. Items with no
     *         modifiers are simply not in the map.
     */
    public Map<Long, List<OrderItemModifierView>> getModifierViewsForOrderItems(Collection<Long> orderItemIds) {
        if (orderItemIds.isEmpty()) {
            return Map.of();                                            // Nothing to look up - skip both queries
        }

        // Query 1: every modifier row for those items
        List<OrderItemModifier> rows = orderItemModifierRepository.findByOrderItemIdIn(orderItemIds);
        if (rows.isEmpty()) {
            return Map.of();                                            // No item has modifiers - skip the name lookup
        }

        // Query 2: the modifiers those rows point to, keyed by ID so each name lookup is instant
        Set<Long> modifierIds = rows.stream().map(OrderItemModifier::getModifierId).collect(Collectors.toSet());
        Map<Long, Modifier> modifiersById = modifierRepository.findAllById(modifierIds).stream()
                .collect(Collectors.toMap(Modifier::getModifierId, Function.identity()));

        // Sorted by row ID so modifiers always print in the order they were saved
        return rows.stream()
                .sorted(Comparator.comparing(OrderItemModifier::getOrderItemModifierId))
                .map(row -> {
                    Modifier modifier = modifiersById.get(row.getModifierId());
                    return new OrderItemModifierView(
                            row.getOrderItemId(),
                            row.getModifierId(),
                            // A placeholder instead of a crash if the modifier was deleted after the order was placed
                            modifier != null ? modifier.getName() : "Modifier " + row.getModifierId(),
                            row.getPriceAdjustment());
                })
                .collect(Collectors.groupingBy(OrderItemModifierView::orderItemId));
    }

    public List<OrderItemModifierView> getModifierViewsForOrder(Long orderId) {                     // Get the chosen modifiers for every item on one order
        List<Long> orderItemIds = orderItemRepository.findByOrderId(orderId).stream()
                .map(OrderItem::getOrderItemId)
                .toList();

        // One flat list - each entry says which order item it belongs to
        return getModifierViewsForOrderItems(orderItemIds).values().stream()
                .flatMap(List::stream)
                .sorted(Comparator.comparing(OrderItemModifierView::orderItemId))       // Stable sort: keeps each item's modifiers in saved order
                .toList();
    }

    // ------------------------------------------------------------------------
    // Ringing in and changing items that have modifiers and/or a kitchen note
    // ------------------------------------------------------------------------

    /**
     * Ring in one item: the ONLY way an item gets onto an order.
     *
     * Every rule about what may be rung in lives here, so no request can skip one:
     *   1. the order must exist and still be open
     *   2. the menu item must exist, be on the menu, and not be 86'd
     *   3. the chosen modifiers must follow the menu's rules (required groups, limits)
     *   4. the price is the menu price plus the modifiers' upcharges - worked out here,
     *      never taken from the request
     *
     * One thing IS taken from the request: where the item sits on the order
     * (ringInPosition). It decides the order items are listed in and nothing else.
     *
     * (There used to be a second, "plain" path for items without modifiers. It saved the
     * price the browser sent and checked none of the above.)
     *
     * Creates a draft order item together with its modifiers and kitchen note.
     *
     * Everything is saved in ONE transaction: if any part fails, nothing is
     * saved. The price is worked out here (menu price + upcharges), not taken
     * from the request.
     *
     * @param request Order, menu item, chosen modifier IDs, and optional note
     * @return The saved order item (status draft, price including upcharges)
     */
    @Transactional
    public OrderItem ringInItem(OrderItemRequest request) {
        // 1. Only onto an open order (see OrderItemService.requireOpenOrder for the floor scenario)
        orderItemService.requireOpenOrder(request.orderId());

        // 2. The menu item must be something that can be ordered right now
        MenuItem menuItem = menuItemRepository.findById(request.menuItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found with id: " + request.menuItemId()));

        // An item taken off the menu cannot be rung in. A screen that loaded the menu before
        // it was removed could still send it.
        if (!Boolean.TRUE.equals(menuItem.getIsActive())) {
            throw new ItemUnavailableException(menuItem.getName() + " is no longer on the menu.");
        }

        // An 86'd item cannot be rung in. The order screen greys it out, but a screen that
        // has not refreshed yet could still send it.
        if (Boolean.TRUE.equals(menuItem.getIs86d())) {
            throw new ItemUnavailableException(menuItem.getName() + " is 86'd and cannot be ordered right now.");
        }

        // Check the choices against the menu's rules BEFORE anything is saved
        List<ModifierOptionView> chosen = validateSelection(menuItem, request.modifierIds());

        OrderItem orderItem = new OrderItem();
        orderItem.setOrderId(request.orderId());
        orderItem.setMenuItemId(menuItem.getMenuItemId());
        orderItem.setQuantity(request.quantity() != null ? request.quantity() : 1);
        orderItem.setPrice(priceWithUpcharges(menuItem, chosen));
        orderItem.setSpecialInstructions(cleanNote(request.specialInstructions()));
        orderItem.setStatus(OrderItemStatus.DRAFT.getValue());                  // Every item starts as a draft with no timer; Send starts the edit window
        orderItem.setIsLocked(false);

        // Where the item sits on the order. The order screen says (it is the only one who
        // knows what the server tapped first: see OrderItem.IN_RING_IN_ORDER). A request
        // that does not say is placed after everything already on the order.
        orderItem.setRingInPosition(request.ringInPosition() != null
                ? request.ringInPosition()
                : orderItemRepository.findHighestRingInPosition(request.orderId()) + 1);

        OrderItem saved = orderItemRepository.save(orderItem);
        saveModifierRows(saved.getOrderItemId(), chosen);
        return saved;
    }

    /**
     * Replace the modifiers and kitchen note on a saved order item (only if not locked).
     *
     * The item's old modifiers are removed and the new set is saved in their
     * place, and the price is worked out again from the new upcharges.
     *
     * @param orderItemId The order item to change
     * @param request The item's complete new set of modifier IDs and its new note
     * @return The updated order item
     */
    @Transactional
    public OrderItem replaceModifiersForOrderItem(Long orderItemId, OrderItemModifiersRequest request) {
        OrderItem existing = orderItemRepository.findById(orderItemId)
                .orElseThrow(() -> new ResourceNotFoundException("Order item not found with id: " + orderItemId));

        // Changing modifiers changes the price, and a paid check must not change
        orderItemService.requireOpenOrder(existing.getOrderId());

        if (existing.getIsLocked()) {
            throw new ItemLockedException("Cannot change modifiers on locked item. Item has been sent to kitchen.");
        }

        MenuItem menuItem = menuItemRepository.findById(existing.getMenuItemId())
                .orElseThrow(() -> new ResourceNotFoundException("Menu item not found with id: " + existing.getMenuItemId()));

        List<ModifierOptionView> chosen = validateSelection(menuItem, request.modifierIds());

        // Out with the old modifiers, in with the new
        orderItemModifierRepository.deleteByOrderItemId(orderItemId);
        saveModifierRows(orderItemId, chosen);

        existing.setPrice(priceWithUpcharges(menuItem, chosen));
        existing.setSpecialInstructions(cleanNote(request.specialInstructions()));

        // An item changed during the edit window gets a fresh window, so the kitchen
        // never receives it half-edited. Same +1 second as sendItemsForOrder in OrderItemService.
        boolean inEditWindow = OrderItemStatus.LIMBO.matches(existing.getStatus());
        if (inEditWindow) {
            existing.setDelayExpiresAt(LocalDateTime.now().plusSeconds(existing.getDelaySeconds() + 1));
        }

        OrderItem saved = orderItemRepository.save(existing);

        // The screen shows one countdown for the whole order, so the other items still
        // counting down get the same fresh window as the one that changed
        if (inEditWindow) {
            orderItemService.restartEditWindow(existing.getOrderId());
        }

        return saved;
    }

    /**
     * Check the chosen modifier IDs against the menu item's modifier groups.
     *
     * Rules: every required group must be answered, no group may have more
     * choices than its maxSelections, and every chosen modifier must belong
     * to one of this item's (active) groups.
     *
     * @return The chosen modifiers, in the same order the picker shows them
     * @throws InvalidModifierSelectionException (HTTP 400) if a rule is broken
     */
    private List<ModifierOptionView> validateSelection(MenuItem menuItem, List<Long> modifierIds) {
        // IDs that have not been matched to a group yet (a Set also ignores an ID sent twice)
        Set<Long> unmatchedIds = new LinkedHashSet<>();
        if (modifierIds != null) {
            modifierIds.stream().filter(Objects::nonNull).forEach(unmatchedIds::add);
        }

        List<ModifierOptionView> chosen = new ArrayList<>();

        for (ModifierGroupView group : menuModifierService.getModifierGroupsForMenuItem(menuItem.getMenuItemId())) {
            // Which of this group's choices were picked? (remove() answers true when the ID was in the set)
            List<ModifierOptionView> pickedFromGroup = new ArrayList<>();
            for (ModifierOptionView option : group.modifiers()) {
                if (unmatchedIds.remove(option.modifierId())) {
                    pickedFromGroup.add(option);
                }
            }

            if (Boolean.TRUE.equals(group.isRequired()) && pickedFromGroup.isEmpty()) {
                throw new InvalidModifierSelectionException(
                        "\"" + group.name() + "\" must be chosen for " + menuItem.getName());
            }
            if (pickedFromGroup.size() > group.maxSelections()) {
                throw new InvalidModifierSelectionException(
                        "\"" + group.name() + "\" allows at most " + group.maxSelections()
                                + " selection(s), but " + pickedFromGroup.size() + " were chosen");
            }

            chosen.addAll(pickedFromGroup);
        }

        // Anything left over is not a choice this menu item offers (wrong item, inactive, or does not exist)
        if (!unmatchedIds.isEmpty()) {
            throw new InvalidModifierSelectionException(
                    "Modifier(s) " + unmatchedIds + " are not available for " + menuItem.getName());
        }

        return chosen;
    }

    // Menu price plus every chosen upcharge = the price of ONE of this item
    private BigDecimal priceWithUpcharges(MenuItem menuItem, List<ModifierOptionView> chosen) {
        BigDecimal price = menuItem.getPrice();
        for (ModifierOptionView option : chosen) {
            price = price.add(option.priceAdjustment());
        }
        return price.max(BigDecimal.ZERO);                              // A discount modifier can never push a price below zero
    }

    // Save one row per chosen modifier. The upcharge is copied onto the row so the
    // order keeps what was actually charged even if the menu's upcharge changes later.
    private void saveModifierRows(Long orderItemId, List<ModifierOptionView> chosen) {
        List<OrderItemModifier> rows = new ArrayList<>();
        for (ModifierOptionView option : chosen) {
            OrderItemModifier row = new OrderItemModifier();
            row.setOrderItemId(orderItemId);
            row.setModifierId(option.modifierId());
            row.setPriceAdjustment(option.priceAdjustment());
            rows.add(row);
        }
        orderItemModifierRepository.saveAll(rows);                      // One batched save instead of one save per modifier
    }

    // Trim the kitchen note; a blank note is stored as "no note" (null)
    private String cleanNote(String note) {
        if (note == null || note.isBlank()) {
            return null;
        }
        return note.trim();
    }
}
