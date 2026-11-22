package com.notapos.controller;

import com.notapos.dto.SearchResultsView;
import com.notapos.enums.SearchPeriod;
import com.notapos.security.Permissions;
import com.notapos.security.StaffSession;
import com.notapos.service.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST API Controller for the search box in the top bar.
 *
 * One endpoint: GET /api/search?q=w7  (and, to look further back for paid
 * checks, &closed=yesterday, 7days, or 30days; today is the default).
 *
 * Anyone signed in may search, but not everyone is sent the same thing: the
 * tip and payment method on a closed check go only to the person who opened
 * it and to managers. The controller passes on who is asking; the services
 * hold the rule.
 *
 * @author CJ
 */
@RestController
@RequestMapping("/api/search")
public class SearchController {

    private final SearchService searchService;

    @Autowired
    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    // q is whatever was typed. It may be anything at all; it is only ever compared with,
    // never run (see SearchService). A "closed" value that is not one of the four becomes a 400.
    @PreAuthorize(Permissions.SEARCH)                                    // Every role (see Permissions)
    @GetMapping                                                         // Search tables, open checks, closed checks, and the menu
    public ResponseEntity<SearchResultsView> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "today") SearchPeriod closed,
            @AuthenticationPrincipal StaffSession currentStaff) {       // The signed-in person (never null: nobody else gets this far)
        return ResponseEntity.ok(searchService.search(q, closed, currentStaff));
    }
}
