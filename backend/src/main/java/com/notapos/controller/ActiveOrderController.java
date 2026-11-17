package com.notapos.controller;

import com.notapos.dto.ActiveOrderView;
import com.notapos.service.ActiveOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API Controller for the Active Orders screen.
 *
 * One endpoint that returns every open order together with its table and
 * items, so the frontend makes a single request instead of four.
 *
 * @author CJ
 */
@RestController
@RequestMapping("/api/orders/active")
public class ActiveOrderController {

    private final ActiveOrderService activeOrderService;

    @Autowired
    public ActiveOrderController(ActiveOrderService activeOrderService) {
        this.activeOrderService = activeOrderService;
    }

    @GetMapping                                                             // Get all open orders with table + items
    public ResponseEntity<List<ActiveOrderView>> getActiveOrders() {
        return ResponseEntity.ok(activeOrderService.getActiveOrders());
    }
}
