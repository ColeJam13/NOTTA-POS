package com.notapos.repository;

import com.notapos.entity.Staff;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Staff entity.
 *
 * @author CJ
 */
@Repository
public interface StaffRepository extends JpaRepository<Staff, Long> {

    List<Staff> findAllByOrderByNameAsc();                              // Everyone, in name order (for the Staff screen)

    List<Staff> findByIsActive(Boolean isActive);                       // Everyone who can (or cannot) sign in

    long countByRoleAndIsActive(String role, Boolean isActive);         // How many active people have a role (used to protect the last manager)
}
