package com.notapos.repository;

import com.notapos.entity.Setting;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

/**
 * Repository for the settings table.
 *
 * Reading a setting is the inherited findById("template").
 *
 * @author CJ
 */
@Repository
public interface SettingRepository extends JpaRepository<Setting, String> {

    /**
     * Set a setting: add its row, or replace the value if the row is already there.
     *
     * WHY ONE SQL STATEMENT and not "look for the row, then insert or update":
     * that is two steps, and two managers pressing APPLY in the same instant
     * would both find no row and both insert, and one would fail on the
     * primary key. "INSERT ... ON CONFLICT ... DO UPDATE" is PostgreSQL's way
     * of saying "insert, or if the key is taken, update that row", done by the
     * database as a single step. It is often called an "upsert".
     *
     * nativeQuery = true: this is PostgreSQL's own SQL, not JPQL, because JPQL
     * has no upsert.
     */
    @Modifying
    @Query(value = "INSERT INTO settings (setting_key, setting_value, updated_at, updated_by_staff_id, updated_by_name) "
            + "VALUES (:key, :value, :at, :staffId, :staffName) "
            + "ON CONFLICT (setting_key) DO UPDATE SET setting_value = EXCLUDED.setting_value, "
            + "updated_at = EXCLUDED.updated_at, updated_by_staff_id = EXCLUDED.updated_by_staff_id, "
            + "updated_by_name = EXCLUDED.updated_by_name",
            nativeQuery = true)
    void put(@Param("key") String key, @Param("value") String value, @Param("at") Instant at,
             @Param("staffId") Long staffId, @Param("staffName") String staffName);
}
