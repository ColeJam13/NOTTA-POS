package com.notapos.controller;

import com.notapos.dto.SettingsView;
import com.notapos.service.OrderCheckService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API Controller for the settings a screen needs from the backend.
 *
 * WHAT: GET /api/settings answers { "taxRate": 0.03 }.
 * WHY:  The order screen shows a running total before anything is saved, so
 *       it has to do that sum itself, and for that it needs the tax rate. It
 *       used to keep its own copy of the number (TAX_RATE in the frontend),
 *       beside the one the backend charges with (notta.tax-rate). Two copies
 *       can disagree: the guest would be shown one total and charged another.
 *       Now there is one number, and the screen asks for it.
 *
 * The rate is read from OrderCheckService, the class that works out every
 * check, and not from the setting directly: what the screen is told is then
 * the very number that is charged, by construction.
 *
 * Open to anyone who is signed in (no @PreAuthorize): everyone who can open
 * the order screen sees its running total.
 *
 * @author CJ
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final OrderCheckService orderCheckService;

    @Autowired
    public SettingsController(OrderCheckService orderCheckService) {
        this.orderCheckService = orderCheckService;
    }

    @GetMapping                                                                 // Get the settings a screen needs
    public ResponseEntity<SettingsView> getSettings() {
        return ResponseEntity.ok(new SettingsView(orderCheckService.getTaxRate()));
    }
}
