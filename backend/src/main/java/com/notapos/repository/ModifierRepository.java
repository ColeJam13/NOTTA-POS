package com.notapos.repository;

import com.notapos.entity.Modifier;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

/**
 * Repository for Modifier entity.
 * 
 * @author CJ
 */

@Repository
public interface ModifierRepository extends JpaRepository<Modifier, Long> {

    List<Modifier> findByModifierGroupId(Long modifierGroupId);

    List<Modifier> findByIsActive(Boolean isActive);

    List<Modifier> findByModifierGroupIdAndIsActive(Long modifierGroupId, Boolean isActive);
}