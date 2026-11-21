package com.notapos.repository;

import com.notapos.entity.Setting;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for the settings table, against real PostgreSQL.
 *
 * WHAT: The one thing this repository does that Spring does not write for us:
 *       put(), which adds a setting's row or replaces its value in a single
 *       SQL statement.
 * WHY:  That statement is PostgreSQL's own ("ON CONFLICT ... DO UPDATE"), so
 *       only a real PostgreSQL can say whether it is right.
 *
 * @author CJ
 */
class SettingRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private SettingRepository settingRepository;

    @Autowired
    private EntityManager entityManager;

    private static final Instant NOON = Instant.parse("2026-11-21T12:00:00Z");

    @BeforeEach
    void setUp() {
        settingRepository.deleteAll();
    }

    // put() goes straight to the database. clear() makes the next read come from the
    // database too, and not from an earlier copy Hibernate is still holding.
    private Setting read(String key) {
        entityManager.flush();
        entityManager.clear();
        return settingRepository.findById(key).orElse(null);
    }

    @Test
    void testPut_WithNoRowYet_ShouldAddIt() {
        // WHAT: Test setting something for the first time
        // WHY: The table starts empty; the first choice has to create the row

        settingRepository.put("template", "plain", NOON, 2L, "Morgan");

        Setting stored = read("template");
        assertNotNull(stored);
        assertEquals("plain", stored.getValue());
        assertEquals(NOON, stored.getUpdatedAt());
        assertEquals(2L, stored.getUpdatedByStaffId());
        assertEquals("Morgan", stored.getUpdatedByName());
    }

    @Test
    void testPut_WithARowAlreadyThere_ShouldReplaceEverythingOnIt() {
        // WHAT: Test setting the same thing a second time, by someone else, later
        // WHY: There is one row per setting. The second choice must replace the first
        //      (value, time, and who), not fail on the primary key and not add a row.

        settingRepository.put("template", "plain", NOON, 2L, "Morgan");
        settingRepository.put("template", "8-bit", NOON.plusSeconds(60), 5L, "Alex");

        assertEquals(1, settingRepository.count());
        Setting stored = read("template");
        assertEquals("8-bit", stored.getValue());
        assertEquals(NOON.plusSeconds(60), stored.getUpdatedAt());
        assertEquals(5L, stored.getUpdatedByStaffId());
        assertEquals("Alex", stored.getUpdatedByName());
    }

    @Test
    void testPut_ShouldLeaveOtherSettingsAlone() {
        // WHAT: Test two different settings side by side
        // WHY: Replacing one must not touch another

        settingRepository.put("template", "plain", NOON, 2L, "Morgan");
        settingRepository.put("something-else", "42", NOON, 2L, "Morgan");
        settingRepository.put("template", "8-bit", NOON, 2L, "Morgan");

        assertEquals(2, settingRepository.count());
        assertEquals("42", read("something-else").getValue());
    }

    @Test
    void testFindById_ForASettingNobodySet_ShouldBeEmpty() {
        // WHAT: Test reading a setting that has no row
        // WHY: "No row" is how the app knows to use the setting's default

        assertTrue(settingRepository.findById("template").isEmpty());
    }
}
