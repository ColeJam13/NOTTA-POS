package com.notapos.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A prize was handed over: this person, for this challenge, marked by this
 * manager, at this moment. A row in the challenge_awards table (migration V9).
 *
 * WHY THIS IS STORED when progress is not: handing over a prize is a fact, and
 * nothing in the checks says it happened. It is written once and never changed.
 * Rows are only ever added through ChallengeAwardRepository.awardOnce, which
 * leaves an existing row alone; this class is for reading them.
 *
 * @author CJ
 */
@Entity
@Table(name = "challenge_awards")
public class ChallengeAward {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "challenge_award_id")
    private Long challengeAwardId;

    @Column(name = "challenge_id", nullable = false, updatable = false)
    private Long challengeId;

    // Who completed the challenge and was given the prize
    @Column(name = "staff_id", nullable = false, updatable = false)
    private Long staffId;

    // A copy of their name, so the row still reads correctly after they have left
    @Column(name = "staff_name", nullable = false, length = 50, updatable = false)
    private String staffName;

    // The manager who marked it
    @Column(name = "awarded_by_staff_id", nullable = false, updatable = false)
    private Long awardedByStaffId;

    @Column(name = "awarded_by_name", nullable = false, length = 50, updatable = false)
    private String awardedByName;

    @Column(name = "awarded_at", nullable = false, updatable = false)
    private Instant awardedAt;

    public ChallengeAward() {}

    public Long getChallengeAwardId() {
        return challengeAwardId;
    }

    public Long getChallengeId() {
        return challengeId;
    }

    public Long getStaffId() {
        return staffId;
    }

    public String getStaffName() {
        return staffName;
    }

    public Long getAwardedByStaffId() {
        return awardedByStaffId;
    }

    public String getAwardedByName() {
        return awardedByName;
    }

    public Instant getAwardedAt() {
        return awardedAt;
    }
}
