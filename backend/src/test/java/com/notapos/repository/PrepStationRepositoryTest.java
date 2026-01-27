package com.notapos.repository;

import com.notapos.entity.PrepStation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Repository tests for PrepStationRepository.
 * 
 * Tests database queries for prep station management using PostgreSQL Testcontainer.
 * 
 * CHANGES FROM ORIGINAL:
 * - Now extends BaseRepositoryTest (provides PostgreSQL container)
 * - Removed @DataJpaTest, @AutoConfigureTestDatabase, @ActiveProfiles (inherited from base)
 * - Tests now run against real PostgreSQL 16 in Docker
 * 
 * @author CJ
 */
class PrepStationRepositoryTest extends BaseRepositoryTest {

    @Autowired
    private PrepStationRepository prepStationRepository;

    private PrepStation kitchen;
    private PrepStation bar;
    private PrepStation inactiveStation;

    @BeforeEach
    void setUp() {
        // Clear database before each test
        prepStationRepository.deleteAll();

        // Create active Kitchen station
        kitchen = new PrepStation();
        kitchen.setName("Kitchen");
        kitchen.setDescription("Main kitchen prep station");
        kitchen.setIsActive(true);
        kitchen = prepStationRepository.save(kitchen);

        // Create active Bar station
        bar = new PrepStation();
        bar.setName("Bar");
        bar.setDescription("Beverage prep station");
        bar.setIsActive(true);
        bar = prepStationRepository.save(bar);

        // Create inactive station
        inactiveStation = new PrepStation();
        inactiveStation.setName("Old Grill");
        inactiveStation.setDescription("Decommissioned station");
        inactiveStation.setIsActive(false);
        inactiveStation = prepStationRepository.save(inactiveStation);
    }

    @Test
    void testSave_ShouldPersistPrepStation() {
        // WHAT: Test saving a new prep station to database
        // WHY: Ensure basic create operation works
        
        // Given - New prep station
        PrepStation newStation = new PrepStation();
        newStation.setName("Dessert Station");
        newStation.setDescription("For plating desserts");
        newStation.setIsActive(true);

        // When - Save to database
        PrepStation saved = prepStationRepository.save(newStation);

        // Then - Should persist with generated ID
        assertNotNull(saved.getPrepStationId());
        assertEquals("Dessert Station", saved.getName());
        assertTrue(saved.getIsActive());
    }

    @Test
    void testFindById_WhenExists_ShouldReturnStation() {
        // WHAT: Test finding prep station by ID
        // WHY: Need to load specific stations for configuration
        
        // Given - Kitchen station exists in database (from setUp)
        
        // When - Find by ID
        Optional<PrepStation> result = prepStationRepository.findById(kitchen.getPrepStationId());

        // Then - Should find the station
        assertTrue(result.isPresent());
        assertEquals("Kitchen", result.get().getName());
        assertTrue(result.get().getIsActive());
    }

    @Test
    void testFindById_WhenNotExists_ShouldReturnEmpty() {
        // WHAT: Test finding non-existent prep station
        // WHY: Handle missing stations gracefully
        
        // Given - Non-existent ID
        
        // When - Try to find
        Optional<PrepStation> result = prepStationRepository.findById(999L);

        // Then - Should return empty
        assertFalse(result.isPresent());
    }

    @Test
    void testFindAll_ShouldReturnAllStations() {
        // WHAT: Test retrieving all prep stations
        // WHY: Get complete list for admin configuration
        
        // Given - 3 stations in database (from setUp)
        
        // When - Find all
        List<PrepStation> stations = prepStationRepository.findAll();

        // Then - Should get all 3 stations
        assertEquals(3, stations.size());
    }

    @Test
    void testFindByName_ShouldReturnStation() {
        // WHAT: Test finding prep station by name
        // WHY: Look up stations by their common names
        
        // Given - Kitchen station exists (from setUp)
        
        // When - Find by name
        Optional<PrepStation> result = prepStationRepository.findByName("Kitchen");

        // Then - Should find the station
        assertTrue(result.isPresent());
        assertEquals("Kitchen", result.get().getName());
        assertEquals("Main kitchen prep station", result.get().getDescription());
    }

    @Test
    void testFindByIsActive_True_ShouldReturnActiveStations() {
        // WHAT: Test finding all active prep stations
        // WHY: Show only operational stations for order routing
        
        // Given - 2 active stations exist (from setUp)
        
        // When - Find active stations
        List<PrepStation> active = prepStationRepository.findByIsActive(true);

        // Then - Should get 2 active stations
        assertEquals(2, active.size());
        assertTrue(active.stream().allMatch(PrepStation::getIsActive));
    }

    @Test
    void testFindByIsActive_False_ShouldReturnInactiveStations() {
        // WHAT: Test finding all inactive prep stations
        // WHY: Show decommissioned stations for records
        
        // Given - 1 inactive station exists (from setUp)
        
        // When - Find inactive stations
        List<PrepStation> inactive = prepStationRepository.findByIsActive(false);

        // Then - Should get 1 inactive station
        assertEquals(1, inactive.size());
        assertFalse(inactive.get(0).getIsActive());
        assertEquals("Old Grill", inactive.get(0).getName());
    }

    @Test
    void testDeleteById_ShouldRemoveStation() {
        // WHAT: Test deleting a prep station
        // WHY: Remove unused stations from system
        
        // Given - Inactive station exists
        Long stationId = inactiveStation.getPrepStationId();
        
        // When - Delete the station
        prepStationRepository.deleteById(stationId);

        // Then - Station should no longer exist
        Optional<PrepStation> deleted = prepStationRepository.findById(stationId);
        assertFalse(deleted.isPresent());
    }

    @Test
    void testUpdate_ShouldModifyExistingStation() {
        // WHAT: Test updating a prep station's fields
        // WHY: Change station descriptions or status
        
        // Given - Kitchen station exists
        Long stationId = kitchen.getPrepStationId();
        
        // When - Update description and status
        kitchen.setDescription("Updated main kitchen");
        kitchen.setIsActive(false);
        PrepStation updated = prepStationRepository.save(kitchen);

        // Then - Changes should persist
        PrepStation reloaded = prepStationRepository.findById(stationId).orElseThrow();
        assertEquals("Updated main kitchen", reloaded.getDescription());
        assertFalse(reloaded.getIsActive());
    }
}