package com.notapos.controller;

import com.notapos.entity.Modifier;
import com.notapos.service.ModifierService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.notapos.testsupport.ControllerTest;
import com.notapos.testsupport.SignIn;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for ModifierController.
 * 
 * Tests REST API endpoints for modifier management.
 * Modifiers are individual options within groups (e.g. "Fries", "Add Bacon +$2").
 * Uses MockMvc to simulate HTTP requests without starting full server.
 * 
 * @author CJ
 */

@ControllerTest(ModifierController.class)
class ModifierControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SignIn signIn;              // Says who a request comes from: .with(signIn.asServer())

    @MockitoBean
    private ModifierService modifierService;

    private Modifier testModifier;

    @BeforeEach
    void setUp() {
        testModifier = new Modifier();
        testModifier.setModifierId(1L);
        testModifier.setModifierGroupId(1L);
        testModifier.setName("Add Bacon");
        testModifier.setPriceAdjustment(new BigDecimal("2.00"));
        testModifier.setIsActive(true);
    }

    @Test
    void testGetAllModifiers_ShouldReturnList() throws Exception {
        // WHAT: Test GET /api/modifiers
        // WHY: Retrieve all modifiers
        
        List<Modifier> modifiers = Arrays.asList(testModifier);
        when(modifierService.getAllModifiers()).thenReturn(modifiers);

        mockMvc.perform(get("/api/modifiers").with(signIn.asServer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Add Bacon"));

        verify(modifierService).getAllModifiers();
    }

    @Test
    void testGetAllModifiers_WithActiveFilter_ShouldReturnActive() throws Exception {
        // WHAT: Test GET /api/modifiers?active=true
        // WHY: Get only active modifiers for menu display
        
        List<Modifier> modifiers = Arrays.asList(testModifier);
        when(modifierService.getActiveModifiers()).thenReturn(modifiers);

        mockMvc.perform(get("/api/modifiers").with(signIn.asServer()).param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isActive").value(true));

        verify(modifierService).getActiveModifiers();
    }

    @Test
    void testGetModifierById_WhenExists_ShouldReturnModifier() throws Exception {
        // WHAT: Test GET /api/modifiers/{id}
        // WHY: Retrieve specific modifier details
        
        when(modifierService.getModifierById(1L)).thenReturn(Optional.of(testModifier));

        mockMvc.perform(get("/api/modifiers/1").with(signIn.asServer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modifierId").value(1))
                .andExpect(jsonPath("$.name").value("Add Bacon"));

        verify(modifierService).getModifierById(1L);
    }

    @Test
    void testGetModifierById_WhenNotExists_ShouldReturn404() throws Exception {
        // WHAT: Test GET /api/modifiers/{id} when not found
        // WHY: Handle missing modifiers
        
        when(modifierService.getModifierById(999L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/modifiers/999").with(signIn.asServer()))
                .andExpect(status().isNotFound());

        verify(modifierService).getModifierById(999L);
    }

    @Test
    void testGetModifiersByGroup_ShouldReturnGroupModifiers() throws Exception {
        // WHAT: Test GET /api/modifiers/group/{modifierGroupId}
        // WHY: Get all modifiers in a specific group
        
        List<Modifier> modifiers = Arrays.asList(testModifier);
        when(modifierService.getModifiersByGroup(1L)).thenReturn(modifiers);

        mockMvc.perform(get("/api/modifiers/group/1").with(signIn.asServer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].modifierGroupId").value(1));

        verify(modifierService).getModifiersByGroup(1L);
    }

    @Test
    void testGetModifiersByGroup_WithActiveFilter_ShouldReturnActiveInGroup() throws Exception {
        // WHAT: Test GET /api/modifiers/group/{modifierGroupId}?active=true
        // WHY: Get only active modifiers in a group
        
        List<Modifier> modifiers = Arrays.asList(testModifier);
        when(modifierService.getActiveModifiersByGroup(1L)).thenReturn(modifiers);

        mockMvc.perform(get("/api/modifiers/group/1").with(signIn.asServer()).param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isActive").value(true));

        verify(modifierService).getActiveModifiersByGroup(1L);
    }

    @Test
    void testCreateModifier_ShouldReturnCreated() throws Exception {
        // WHAT: Test POST /api/modifiers
        // WHY: Create new modifier option
        
        when(modifierService.createModifier(any(Modifier.class))).thenReturn(testModifier);

        mockMvc.perform(post("/api/modifiers").with(signIn.asManager())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Add Bacon\",\"priceAdjustment\":2.00}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Add Bacon"));

        verify(modifierService).createModifier(any(Modifier.class));
    }

    @Test
    void testUpdateModifier_ShouldReturnUpdated() throws Exception {
        // WHAT: Test PUT /api/modifiers/{id}
        // WHY: Update modifier details or pricing
        
        when(modifierService.updateModifier(eq(1L), any(Modifier.class))).thenReturn(testModifier);

        mockMvc.perform(put("/api/modifiers/1").with(signIn.asManager())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Add Bacon\",\"priceAdjustment\":2.50}"))
                .andExpect(status().isOk());

        verify(modifierService).updateModifier(eq(1L), any(Modifier.class));
    }

    @Test
    void testDeleteModifier_ShouldReturn204() throws Exception {
        // WHAT: Test DELETE /api/modifiers/{id}
        // WHY: Remove unused modifiers
        
        doNothing().when(modifierService).deleteModifier(1L);

        mockMvc.perform(delete("/api/modifiers/1").with(signIn.asManager()))
                .andExpect(status().isNoContent());

        verify(modifierService).deleteModifier(1L);
    }
}