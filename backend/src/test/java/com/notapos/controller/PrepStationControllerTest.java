package com.notapos.controller;

import com.notapos.entity.PrepStation;
import com.notapos.service.PrepStationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.notapos.testsupport.ControllerTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for PrepStationController.
 * 
 * Tests REST API endpoints for prep station management (Kitchen, Bar, etc.).
 * Prep stations route menu items to different kitchen areas.
 * Uses MockMvc to simulate HTTP requests without starting full server.
 * 
 * @author CJ
 */

@ControllerTest(PrepStationController.class)
class PrepStationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PrepStationService prepStationService;

    private PrepStation testStation;

    @BeforeEach
    void setUp() {
        testStation = new PrepStation();
        testStation.setPrepStationId(1L);
        testStation.setName("Kitchen");
        testStation.setDescription("Main kitchen prep area");
        testStation.setIsActive(true);
    }

    @Test
    void testGetAllPrepStations_ShouldReturnList() throws Exception {
        // WHAT: Test GET /api/prep-stations
        // WHY: Retrieve all prep stations
        
        List<PrepStation> stations = Arrays.asList(testStation);
        when(prepStationService.getAllPrepStations()).thenReturn(stations);

        mockMvc.perform(get("/api/prep-stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Kitchen"));

        verify(prepStationService).getAllPrepStations();
    }

    @Test
    void testGetAllPrepStations_WithActiveFilter_ShouldReturnActive() throws Exception {
        // WHAT: Test GET /api/prep-stations?active=true
        // WHY: Get only active stations for order routing
        
        List<PrepStation> stations = Arrays.asList(testStation);
        when(prepStationService.getActivePrepStations()).thenReturn(stations);

        mockMvc.perform(get("/api/prep-stations").param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isActive").value(true));

        verify(prepStationService).getActivePrepStations();
    }

    @Test
    void testGetPrepStationById_WhenExists_ShouldReturnStation() throws Exception {
        // WHAT: Test GET /api/prep-stations/{id}
        // WHY: Retrieve specific prep station
        
        when(prepStationService.getPrepStationById(1L)).thenReturn(Optional.of(testStation));

        mockMvc.perform(get("/api/prep-stations/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.prepStationId").value(1))
                .andExpect(jsonPath("$.name").value("Kitchen"));

        verify(prepStationService).getPrepStationById(1L);
    }

    @Test
    void testGetPrepStationById_WhenNotExists_ShouldReturn404() throws Exception {
        // WHAT: Test GET /api/prep-stations/{id} when not found
        // WHY: Handle missing stations
        
        when(prepStationService.getPrepStationById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/prep-stations/999"))
                .andExpect(status().isNotFound());

        verify(prepStationService).getPrepStationById(999L);
    }

    @Test
    void testCreatePrepStation_ShouldReturnCreated() throws Exception {
        // WHAT: Test POST /api/prep-stations
        // WHY: Create new prep station (Bar, Expo, etc.)
        
        when(prepStationService.createPrepStation(any(PrepStation.class))).thenReturn(testStation);

        mockMvc.perform(post("/api/prep-stations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Kitchen\",\"description\":\"Main kitchen\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Kitchen"));

        verify(prepStationService).createPrepStation(any(PrepStation.class));
    }

    @Test
    void testUpdatePrepStation_ShouldReturnUpdated() throws Exception {
        // WHAT: Test PUT /api/prep-stations/{id}
        // WHY: Update station details
        
        when(prepStationService.updatePrepStation(eq(1L), any(PrepStation.class))).thenReturn(testStation);

        mockMvc.perform(put("/api/prep-stations/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated Kitchen\"}"))
                .andExpect(status().isOk());

        verify(prepStationService).updatePrepStation(eq(1L), any(PrepStation.class));
    }

    @Test
    void testDeletePrepStation_ShouldReturn204() throws Exception {
        // WHAT: Test DELETE /api/prep-stations/{id}
        // WHY: Remove unused prep stations
        
        doNothing().when(prepStationService).deletePrepStation(1L);

        mockMvc.perform(delete("/api/prep-stations/1"))
                .andExpect(status().isNoContent());

        verify(prepStationService).deletePrepStation(1L);
    }
}