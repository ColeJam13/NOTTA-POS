package com.notapos.repository;

import com.notapos.entity.ChallengeAward;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Repository for ChallengeAward entity: the prizes that have been handed over.
 *
 * @author CJ
 */
@Repository
public interface ChallengeAwardRepository extends JpaRepository<ChallengeAward, Long> {

    // The awards on MANY challenges in one query (avoids one query per challenge)
    List<ChallengeAward> findByChallengeIdIn(Collection<Long> challengeIds);

    boolean existsByChallengeId(Long challengeId);              // Has any prize been marked awarded on this challenge?

    /**
     * Record that a person was given a challenge's prize, once.
     *
     * "ON CONFLICT DO NOTHING" means: if this person already has a row for this
     * challenge (the table allows one each), leave it exactly as it is. So a second
     * press of AWARDED, or two managers pressing it at the same moment, changes
     * nothing and is not an error, and the row keeps saying who marked it FIRST.
     * (The same idea as dismissing a notification twice.)
     *
     * A native query, because JPQL has no INSERT of this kind. Every value is a
     * parameter; nothing is joined into the text.
     *
     * @return 1 if the award was recorded now, 0 if it already was
     */
    @Modifying
    @Query(value = "INSERT INTO challenge_awards (challenge_id, staff_id, staff_name, awarded_by_staff_id, awarded_by_name, awarded_at) "
            + "VALUES (:challengeId, :staffId, :staffName, :awardedByStaffId, :awardedByName, :awardedAt) "
            + "ON CONFLICT (challenge_id, staff_id) DO NOTHING", nativeQuery = true)
    int awardOnce(Long challengeId, Long staffId, String staffName, Long awardedByStaffId, String awardedByName, Instant awardedAt);
}
