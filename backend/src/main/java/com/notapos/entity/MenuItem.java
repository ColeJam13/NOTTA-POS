package com.notapos.entity;

import jakarta.validation.constraints.*;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Entity representing a menu item in the restaurant.
 * 
 * Menu items are the products customers can order.
 * Each item belongs to a category and is prepared at a specific prep station.
 * 
 * @author CJ
 */

@Entity
@Table(name = "menu_items")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "menu_item_id")
    private Long menuItemId;

    @NotBlank(message = "Menu item name is required")
    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    @NotNull(message = "Price is required")
    @PositiveOrZero(message = "Price cannot be negative")
    @Column(name = "price", nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @NotBlank(message = "Category is required")
    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "prep_station_id")
    private Long prepStationId;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    // 86'd = the kitchen or bar has run out of this for now. Different from isActive:
    // an inactive item is off the menu and hidden; an 86'd item stays on the menu, greyed
    // out, so servers can see it is out instead of wondering where it went.
    // "default false" fills the column in for rows that already exist and for rows
    // inserted by the seed file, which does not mention it.
    @Column(name = "is_86d", nullable = false, columnDefinition = "boolean default false")
    private Boolean is86d = false;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public MenuItem() {}

    public Long getMenuItemId() {
        return menuItemId;
    }

    public void setMenuItemId(Long menuItemId) {
        this.menuItemId = menuItemId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public Long getPrepStationId() {
        return prepStationId;
    }

    public void setPrepStationId(Long prepStationId) {
        this.prepStationId = prepStationId;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public Boolean getIs86d() {
        return is86d;
    }

    public void setIs86d(Boolean is86d) {
        this.is86d = is86d;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

}
