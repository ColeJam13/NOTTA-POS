package com.notapos.repository;

import com.notapos.entity.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for StaffRepository.
 *
 * Tests database queries for staff using PostgreSQL Testcontainer.
 *
 * @author CJ
 */
class StaffRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private StaffRepository staffRepository;

    @BeforeEach
    void setUp() {
        // Clear database before each test
        staffRepository.deleteAll();

        save("Sarah", "server", true);
        save("Morgan", "manager", true);
        save("Alex", "manager", false);          // A manager who no longer works here
    }

    private Staff save(String name, String role, boolean active) {
        Staff staff = new Staff();
        staff.setName(name);
        staff.setRole(role);
        staff.setPinHash("hash-for-" + name);    // The hash's content does not matter to these queries
        staff.setIsActive(active);
        return staffRepository.save(staff);
    }

    @Test
    void testSave_ShouldGiveAnIdAndACreationTime() {
        // WHAT: Test saving a staff member
        // WHY: Ensure basic create works and the creation time is filled in

        Staff saved = save("Jamie", "host", true);

        assertNotNull(saved.getStaffId());
        assertNotNull(saved.getCreatedAt());
    }

    @Test
    void testFindAllByOrderByNameAsc_ShouldReturnEveryoneInNameOrder() {
        // WHAT: Test listing all staff
        // WHY: The Staff screen shows everyone, active or not, alphabetically

        List<Staff> staff = staffRepository.findAllByOrderByNameAsc();

        assertEquals(List.of("Alex", "Morgan", "Sarah"), staff.stream().map(Staff::getName).toList());
    }

    @Test
    void testFindByIsActive_ShouldSeparateThoseWhoCanSignIn() {
        // WHAT: Test finding active staff
        // WHY: Sign-in only checks PINs of people who still work here

        List<Staff> active = staffRepository.findByIsActive(true);

        assertEquals(2, active.size());
        assertTrue(active.stream().noneMatch(staff -> staff.getName().equals("Alex")));
    }

    @Test
    void testCountByRoleAndIsActive_ShouldCountOnlyActivePeopleWithThatRole() {
        // WHAT: Test counting active managers
        // WHY: The last active manager cannot be removed. Alex is a manager but inactive,
        //      so he must not count.

        assertEquals(1, staffRepository.countByRoleAndIsActive("manager", true));
        assertEquals(1, staffRepository.countByRoleAndIsActive("server", true));
        assertEquals(0, staffRepository.countByRoleAndIsActive("kitchen", true));
    }
}
