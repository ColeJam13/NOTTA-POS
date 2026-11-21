package com.notapos.controller;

import com.notapos.entity.ModifierGroup;
import com.notapos.service.ModifierGroupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.notapos.testsupport.ControllerTest;
import com.notapos.testsupport.SignIn;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
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
 * Controller tests for ModifierGroupController.
 * 
 * Tests REST API endpoints for modifier group management.
 * Modifier groups organize customization options (e.g. "Choose a Side", "Add Protein").
 * Uses MockMvc to simulate HTTP requests without starting full server.
 * 
 * @author CJ
 */

@ControllerTest(ModifierGroupController.class)
class ModifierGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignIn signIn;              // Says who a request comes from: .with(signIn.asServer())

    @MockitoBean
    private ModifierGroupService modifierGroupService;

    private ModifierGroup testGroup;

    @BeforeEach
    void setUp() {
        testGroup = new ModifierGroup();
        testGroup.setModifierGroupId(1L);
        testGroup.setName("Choose a Side");
        testGroup.setDescription("Pick your side dish");
        testGroup.setIsRequired(true);
        testGroup.setMaxSelections(1);
        testGroup.setIsActive(true);
    }

    @Test
    void testGetAllModifierGroups_ShouldReturnList() throws Exception {
        // WHAT: Test GET /api/modifier-groups
        // WHY: Retrieve all modifier groups
        
        List<ModifierGroup> groups = Arrays.asList(testGroup);
        when(modifierGroupService.getAllModifierGroups()).thenReturn(groups);

        mockMvc.perform(get("/api/modifier-groups").with(signIn.asServer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Choose a Side"));

        verify(modifierGroupService).getAllModifierGroups();
    }

    @Test
    void testGetAllModifierGroups_WithActiveFilter_ShouldReturnActive() throws Exception {
        // WHAT: Test GET /api/modifier-groups?active=true
        // WHY: Get only active groups for menu display
        
        List<ModifierGroup> groups = Arrays.asList(testGroup);
        when(modifierGroupService.getActiveModifierGroups()).thenReturn(groups);

        mockMvc.perform(get("/api/modifier-groups").with(signIn.asServer()).param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isActive").value(true));

        verify(modifierGroupService).getActiveModifierGroups();
    }

    @Test
    void testGetModifierGroupById_WhenExists_ShouldReturnGroup() throws Exception {
        // WHAT: Test GET /api/modifier-groups/{id}
        // WHY: Retrieve specific modifier group
        
        when(modifierGroupService.getModifierGroupById(1L)).thenReturn(Optional.of(testGroup));

        mockMvc.perform(get("/api/modifier-groups/1").with(signIn.asServer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modifierGroupId").value(1))
                .andExpect(jsonPath("$.name").value("Choose a Side"));

        verify(modifierGroupService).getModifierGroupById(1L);
    }

    @Test
    void testGetModifierGroupById_WhenNotExists_ShouldReturn404() throws Exception {
        // WHAT: Test GET /api/modifier-groups/{id} when not found
        // WHY: Handle missing groups
        
        when(modifierGroupService.getModifierGroupById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/modifier-groups/999").with(signIn.asServer()))
                .andExpect(status().isNotFound());

        verify(modifierGroupService).getModifierGroupById(999L);
    }

    @Test
    void testCreateModifierGroup_ShouldReturnCreated() throws Exception {
        // WHAT: Test POST /api/modifier-groups
        // WHY: Create new modifier group
        
        when(modifierGroupService.createModifierGroup(any(ModifierGroup.class))).thenReturn(testGroup);

        mockMvc.perform(post("/api/modifier-groups").with(signIn.asManager())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Choose a Side\",\"isRequired\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Choose a Side"));

        verify(modifierGroupService).createModifierGroup(any(ModifierGroup.class));
    }

    @Test
    void testUpdateModifierGroup_ShouldReturnUpdated() throws Exception {
        // WHAT: Test PUT /api/modifier-groups/{id}
        // WHY: Update group details
        
        when(modifierGroupService.updateModifierGroup(eq(1L), any(ModifierGroup.class))).thenReturn(testGroup);

        mockMvc.perform(put("/api/modifier-groups/1").with(signIn.asManager())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Updated Side\"}"))
                .andExpect(status().isOk());

        verify(modifierGroupService).updateModifierGroup(eq(1L), any(ModifierGroup.class));
    }

    @Test
    void testDeleteModifierGroup_ShouldReturn204() throws Exception {
        // WHAT: Test DELETE /api/modifier-groups/{id}
        // WHY: Remove unused modifier groups
        
        doNothing().when(modifierGroupService).deleteModifierGroup(1L);

        mockMvc.perform(delete("/api/modifier-groups/1").with(signIn.asManager()))
                .andExpect(status().isNoContent());

        verify(modifierGroupService).deleteModifierGroup(1L);
    }
}