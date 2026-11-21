package com.notapos.controller;

import com.notapos.dto.SettingsView;
import com.notapos.dto.TemplateRequest;
import com.notapos.security.Permissions;
import com.notapos.security.StaffSession;
import com.notapos.service.OrderCheckService;
import com.notapos.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API Controller for the settings a screen needs from the backend.
 *
 * WHAT: GET /api/settings answers { "taxRate": 0.03, "template": "8-bit" }.
 *       PUT /api/settings/template lets a manager choose the template.
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
 * THE TEMPLATE is the look every screen is drawn in. The backend keeps only
 * its name (SettingsService), so that every terminal and phone shows the same
 * one. The app asks for it at sign-in and every few seconds after, which is
 * how a manager's change reaches the other terminals.
 *
 * Reading is open to anyone who is signed in (no @PreAuthorize): everyone who
 * can open the order screen sees its running total, and every terminal has to
 * draw itself. Choosing the template is for managers only.
 *
 * @author CJ
 */
@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final OrderCheckService orderCheckService;
    private final SettingsService settingsService;

    @Autowired
    public SettingsController(OrderCheckService orderCheckService, SettingsService settingsService) {
        this.orderCheckService = orderCheckService;
        this.settingsService = settingsService;
    }

    @GetMapping                                                                 // Get the settings a screen needs
    public ResponseEntity<SettingsView> getSettings() {
        return ResponseEntity.ok(currentSettings());
    }

    // Choose the restaurant's template (managers only). Answers with the settings as they
    // now stand, so the screen that made the change redraws from one answer.
    @PreAuthorize(Permissions.MANAGER_ONLY)
    @PutMapping("/template")
    public ResponseEntity<SettingsView> chooseTemplate(
            @Valid @RequestBody TemplateRequest request,
            @AuthenticationPrincipal StaffSession currentStaff) {            // Who is choosing: recorded with the change
        settingsService.setTemplate(request.template(), currentStaff);
        return ResponseEntity.ok(currentSettings());
    }

    private SettingsView currentSettings() {
        return new SettingsView(orderCheckService.getTaxRate(), settingsService.getTemplate());
    }
}
