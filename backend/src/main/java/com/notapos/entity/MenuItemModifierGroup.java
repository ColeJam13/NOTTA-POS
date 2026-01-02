package com.notapos.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity representing the link between menu items and modifier groups.
 * 
 * This is a junction table that defines which modifier groups apply to which menu items.
 * Example: "Chicken Cutty" menu item has "Choose a Side" and "Add Protein" modifier groups.
 * 
 * @author CJ
 */

@Entity
@Table(name = "menu_item_modifier_groups")
public class MenuItemModifierGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "menu_item_modifier_group_id")
    private Long menuItemModifierGroupId;

    @Column(name = "menu_item_id", nullable = false)
    private Long menuItemId;

    @Column(name = "modifier_group_id", nullable = false)
    private Long modifierGroupId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public MenuItemModifierGroup() {}

    public Long getMenuItemModifierGroupId() {
        return menuItemModifierGroupId;
    }

    public void setMenuItemModifierGroupId(Long menuItemModifierGroupId) {
        this.menuItemModifierGroupId = menuItemModifierGroupId;
    }

    public Long getMenuItemId() {
        return menuItemId;
    }

    public void setMenuItemId(Long menuItemId) {
        this.menuItemId = menuItemId;
    }

    public Long getModifierGroupId() {
        return modifierGroupId;
    }

    public void setModifierGroupId(Long modifierGroupId) {
        this.modifierGroupId = modifierGroupId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
}
