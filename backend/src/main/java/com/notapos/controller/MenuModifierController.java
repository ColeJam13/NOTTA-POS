package com.notapos.controller;

import com.notapos.dto.MenuItemModifiersView;
import com.notapos.service.MenuModifierService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST API Controller for the order screen's modifier picker.
 *
 * One endpoint that returns, for every menu item that has modifiers, its
 * groups with their choices already attached. The order screen loads this
 * once next to the menu, so tapping an item never has to wait on a request
 * to find out whether a picker should open.
 *
 * @author CJ
 */
@RestController
@RequestMapping("/api/menu-items/modifier-groups")
public class MenuModifierController {

    private final MenuModifierService menuModifierService;

    @Autowired
    public MenuModifierController(MenuModifierService menuModifierService) {
        this.menuModifierService = menuModifierService;
    }

    @GetMapping                                                                 // Get modifier groups (with choices) for every menu item that has any
    public ResponseEntity<List<MenuItemModifiersView>> getModifierGroupsForMenu() {
        return ResponseEntity.ok(menuModifierService.getModifierGroupsForMenu());
    }
}
