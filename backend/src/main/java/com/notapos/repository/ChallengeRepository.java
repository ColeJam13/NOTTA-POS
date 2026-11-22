package com.notapos.repository;

import com.notapos.entity.Challenge;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Challenge entity.
 *
 * @author CJ
 */
@Repository
public interface ChallengeRepository extends JpaRepository<Challenge, Long> {

    // Challenges that have not ended: the ones running now and the ones still to start.
    // The one ending soonest comes first.
    List<Challenge> findByEndsAtAfterOrderByEndsAtAscChallengeIdAsc(Instant now);

    // Challenges that have ended, the most recently ended first, and only so many
    // (Limit.of(20)): every one listed has its progress worked out from the checks,
    // and a restaurant's list of old challenges only grows.
    List<Challenge> findByEndsAtLessThanEqualOrderByEndsAtDescChallengeIdDesc(Instant now, Limit limit);

    /**
     * Find a challenge and hold it until the transaction ends, so nothing else can
     * change it, delete it, or mark an award on it in the meantime.
     *
     * WHY: "there is no award on this challenge, so it may be deleted" is two steps:
     *      read, then act. Without the lock another manager's AWARDED could land
     *      between them. Everything that changes a challenge or its awards finds it
     *      this way; reading the list does not. (The same idea as
     *      OrderRepository.findByIdLockedForUpdate.)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)                                                   // SQL: SELECT ... FOR UPDATE
    @Query("SELECT c FROM Challenge c WHERE c.challengeId = :challengeId")
    Optional<Challenge> findByIdLockedForUpdate(Long challengeId);
}
