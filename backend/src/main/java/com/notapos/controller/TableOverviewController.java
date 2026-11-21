package com.notapos.controller;

import com.notapos.dto.TablesOverviewView;
import com.notapos.security.StaffSession;
import com.notapos.service.TableOverviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API Controller for the Tables and Quick Orders screens.
 *
 * One endpoint that answers either screen with everything it shows:
 * the tables with an open check, and the most recent paid checks.
 *
 * Anyone signed in may read it, but not everyone is sent the same thing: the
 * tip and payment method on a closed check go only to the person who opened
 * it and to managers. The controller passes on who is asking;
 * TableOverviewService holds the rule.
 *
 * @author CJ
 */

@RestController
@RequestMapping("/api/tables/overview")
public class TableOverviewController {

    private final TableOverviewService tableOverviewService;

    @Autowired
    public TableOverviewController(TableOverviewService tableOverviewService) {
        this.tableOverviewService = tableOverviewService;
    }

    @GetMapping                                                         // Get active tables + recent closed checks (quickOrders=true for the Quick Orders screen)
    public ResponseEntity<TablesOverviewView> getOverview(
            @RequestParam(defaultValue = "false") boolean quickOrders,
            @AuthenticationPrincipal StaffSession currentStaff) {       // The signed-in person (never null: nobody else gets this far)
        return ResponseEntity.ok(tableOverviewService.getOverview(quickOrders, currentStaff));
    }
}
