package com.notapos.repository;

import com.notapos.entity.Modifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;

/**
 * Repository for Modifier entity.
 * 
 * @author CJ
 */

@Repository
public interface ModifierRepository extends JpaRepository<Modifier, Long> {

    List<Modifier> findByModifierGroupId(Long modifierGroupId);                                 // Find modifier by group ID

    List<Modifier> findByIsActive(Boolean isActive);                                            // Find modifier by status

    List<Modifier> findByModifierGroupIdAndIsActive(Long modifierGroupId, Boolean isActive);    // Find modifier by group and status

    List<Modifier> findByModifierGroupIdInAndIsActive(Collection<Long> modifierGroupIds, Boolean isActive);    // Find modifiers for MANY groups in one query (avoids one query per group)
}