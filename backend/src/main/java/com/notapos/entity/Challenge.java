package com.notapos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A challenge a manager has made: "sell 15 appetizers today", "reach $500 in
 * sales this week". A row in the challenges table (migration V9, which explains
 * each column).
 *
 * WHAT IS NOT HERE: anybody's progress. How far each person has got is worked
 * out from the checks every time it is asked for (ChallengeService), so it can
 * never disagree with them. There is no list of participants either: everyone
 * who opens checks in the period is in the challenge.
 *
 * startsAt, endsAt, and createdAt are moments (Instant, "timestamptz" columns),
 * as every table added since V4 uses.
 *
 * @author CJ
 */
@Entity
@Table(name = "challenges")
public class Challenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "challenge_id")
    private Long challengeId;

    @Column(name = "name", nullable = false, length = 60)
    private String name;

    @Column(name = "description", length = 300)
    private String description;

    // A ChallengeMeasure's value: "items_sold", "total_sales", "check_average", "biggest_check"
    @Column(name = "measure", nullable = false, length = 20)
    private String measure;

    // What a person has to reach: a number of items, or dollars
    @Column(name = "target", nullable = false, precision = 10, scale = 2)
    private BigDecimal target;

    // items_sold only, and exactly one of the two: the category whose items count...
    @Column(name = "menu_category", length = 50)
    private String menuCategory;

    // ...or the one menu item that counts
    @Column(name = "menu_item_id")
    private Long menuItemId;

    // check_average only: how many checks a person needs before their average counts
    @Column(name = "min_checks")
    private Integer minChecks;

    @Column(name = "starts_at", nullable = false)
    private Instant startsAt;

    @Column(name = "ends_at", nullable = false)
    private Instant endsAt;

    // Optional words a manager writes. The app pays nothing.
    @Column(name = "prize", length = 100)
    private String prize;

    @Column(name = "created_by_staff_id", nullable = false, updatable = false)
    private Long createdByStaffId;

    // A copy of their name, so the row still reads correctly after they have left
    @Column(name = "created_by_name", nullable = false, length = 50, updatable = false)
    private String createdByName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Challenge() {}

    public Long getChallengeId() {
        return challengeId;
    }

    public void setChallengeId(Long challengeId) {
        this.challengeId = challengeId;
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

    public String getMeasure() {
        return measure;
    }

    public void setMeasure(String measure) {
        this.measure = measure;
    }

    public BigDecimal getTarget() {
        return target;
    }

    public void setTarget(BigDecimal target) {
        this.target = target;
    }

    public String getMenuCategory() {
        return menuCategory;
    }

    public void setMenuCategory(String menuCategory) {
        this.menuCategory = menuCategory;
    }

    public Long getMenuItemId() {
        return menuItemId;
    }

    public void setMenuItemId(Long menuItemId) {
        this.menuItemId = menuItemId;
    }

    public Integer getMinChecks() {
        return minChecks;
    }

    public void setMinChecks(Integer minChecks) {
        this.minChecks = minChecks;
    }

    public Instant getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(Instant startsAt) {
        this.startsAt = startsAt;
    }

    public Instant getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(Instant endsAt) {
        this.endsAt = endsAt;
    }

    public String getPrize() {
        return prize;
    }

    public void setPrize(String prize) {
        this.prize = prize;
    }

    public Long getCreatedByStaffId() {
        return createdByStaffId;
    }

    public void setCreatedByStaffId(Long createdByStaffId) {
        this.createdByStaffId = createdByStaffId;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
