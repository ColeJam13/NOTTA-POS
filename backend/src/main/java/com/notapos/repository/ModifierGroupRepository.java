package com.notapos.repository;


import com.notapos.entity.ModifierGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

/**
 * Repository for ModifierGroup entity.
 * 
 * @author CJ
 */

@Repository
public interface ModifierGroupRepository extends JpaRepository<ModifierGroup, Long> {

    Optional<ModifierGroup> findByName(String name);

    List<ModifierGroup> findByIsActive(Boolean isActive);

    List<ModifierGroup> findByIsRequired(Boolean isRequired);
}



