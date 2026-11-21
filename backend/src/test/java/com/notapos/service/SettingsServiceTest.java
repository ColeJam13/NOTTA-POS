package com.notapos.service;

import com.notapos.entity.Setting;
import com.notapos.enums.StaffRole;
import com.notapos.repository.SettingRepository;
import com.notapos.security.StaffSession;
import com.notapos.testsupport.MutableClock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for SettingsService: the restaurant's template.
 *
 * @author CJ
 */
@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    @Mock
    private SettingRepository settingRepository;

    private MutableClock clock;
    private SettingsService settingsService;

    private final StaffSession morgan = new StaffSession(2L, "Morgan", StaffRole.MANAGER, Instant.now(), false);

    @BeforeEach
    void setUp() {
        clock = new MutableClock();
        settingsService = new SettingsService(settingRepository, clock);
    }

    // A stored setting. Setting has no setters (it is only ever read), so the test fakes one.
    private Setting stored(String value) {
        Setting setting = mock(Setting.class);
        when(setting.getValue()).thenReturn(value);
        return setting;
    }

    @Test
    void testGetTemplate_WithNothingChosen_ShouldReturnTheDefault() {
        // WHAT: Test reading the template when the settings table has no row for it
        // WHY: Every restaurant starts here, and its screens must look as they always have

        when(settingRepository.findById("template")).thenReturn(Optional.empty());

        assertEquals("8-bit", settingsService.getTemplate());
    }

    @Test
    void testGetTemplate_WithOneChosen_ShouldReturnIt() {
        // WHAT: Test reading the template a manager chose

        Setting plain = stored("plain");
        when(settingRepository.findById("template")).thenReturn(Optional.of(plain));

        assertEquals("plain", settingsService.getTemplate());
    }

    @Test
    void testSetTemplate_ShouldStoreTheNameWithWhoChoseItAndWhen() {
        // WHAT: Test choosing a template
        // WHY: The row records who changed the look of every screen, and when

        when(settingRepository.findById("template")).thenReturn(Optional.empty());
        Instant now = clock.instant();

        String result = settingsService.setTemplate("coffee-shop", morgan);

        assertEquals("coffee-shop", result);
        verify(settingRepository).put("template", "coffee-shop", now, 2L, "Morgan");
    }

    @Test
    void testSetTemplate_WithSomethingThatIsNotAName_ShouldThrowAndStoreNothing() {
        // WHAT: Test the service's own check of the name
        // WHY: The controller refuses these first, with a 400. This is the same rule
        //      again for any caller that did not come through the controller: the
        //      value ends up in the page, so nothing but a plain name may be stored.

        for (String notAName : new String[]{null, "", " ", "Plain", "coffee shop", "-plain", "plain-", "a--b", "<b>", "x\"y",
                "a-name-that-is-longer-than-thirty-characters"}) {
            assertThrows(IllegalArgumentException.class, () -> settingsService.setTemplate(notAName, morgan), "name: " + notAName);
        }

        verify(settingRepository, never()).put(any(), any(), any(), any(), any());
    }

    @Test
    void testSetTemplate_ShouldAcceptTheNamesTemplatesHave() {
        // WHAT: Test the names in the plan, and one with digits
        // WHY: The check must not be so strict that it refuses a real template

        when(settingRepository.findById("template")).thenReturn(Optional.empty());

        for (String name : List.of("8-bit", "plain", "coffee-shop", "nightclub", "theme2")) {
            assertEquals(name, settingsService.setTemplate(name, morgan));
        }
    }
}
