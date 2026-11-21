package com.notapos.service;

import com.notapos.entity.Setting;
import com.notapos.repository.SettingRepository;
import com.notapos.security.StaffSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * Service layer for the settings a manager chooses while the app is running.
 *
 * Today that is one: the restaurant's TEMPLATE, the look every screen is
 * drawn in.
 *
 * WHAT THE BACKEND KNOWS ABOUT TEMPLATES: only the name of the one in use.
 * The templates themselves (colors, fonts, shapes) are stylesheets in the
 * frontend, in src/templates, and the frontend is the one list of which exist.
 * So this class checks that a name LOOKS like a name and nothing more. A name
 * no frontend recognizes is harmless: the screens draw the default.
 *
 * WHY THE NAME IS CHECKED AT ALL: the frontend writes it into the page (it is
 * the attribute that picks the stylesheet). A value that could hold a quote,
 * a space, or an angle bracket is a value somebody will one day try to put
 * code in. Lower-case letters, digits, and single hyphens are all a name
 * needs, so that is all that is ever stored.
 *
 * (The tax rate is a setting too, but not one of these: it lives in
 * application.properties and OrderCheckService reads it at start-up.)
 *
 * @author CJ
 */
@Service
@Transactional(readOnly = true)      // Every method runs in a database transaction; read-only unless a method says otherwise
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    // The row of the settings table that holds the template's name
    static final String TEMPLATE_KEY = "template";

    // The template a restaurant has until a manager chooses another: the look the app
    // has always had (the owner's decision D9)
    public static final String DEFAULT_TEMPLATE = "8-bit";

    // What a template's name may look like: "8-bit", "plain", "coffee-shop".
    // Used here and on the request (TemplateRequest), so the rule is written once.
    public static final String TEMPLATE_NAME_PATTERN = "[a-z0-9]+(-[a-z0-9]+)*";
    public static final int TEMPLATE_NAME_MAX_LENGTH = 30;

    private final SettingRepository settingRepository;
    private final Clock clock;                  // "What time is it now?" (a test can hand in its own)

    public SettingsService(SettingRepository settingRepository, Clock clock) {
        this.settingRepository = settingRepository;
        this.clock = clock;
    }

    public String getTemplate() {               // The template in use, or the default when none was ever chosen
        return settingRepository.findById(TEMPLATE_KEY)
                .map(Setting::getValue)
                .orElse(DEFAULT_TEMPLATE);
    }

    /**
     * Choose the restaurant's template. Every terminal picks the change up the
     * next time it asks for the settings.
     *
     * @param template The template's name, e.g. "plain"
     * @param chosenBy The manager choosing it: recorded on the row and in the log
     * @return The name as stored
     */
    @Transactional
    public String setTemplate(String template, StaffSession chosenBy) {
        // The controller has already refused a badly formed name with a 400 (TemplateRequest).
        // This is the same rule again, for any caller that did not come through it.
        if (template == null || template.length() > TEMPLATE_NAME_MAX_LENGTH || !template.matches(TEMPLATE_NAME_PATTERN)) {
            throw new IllegalArgumentException("Not a template name");
        }

        String before = getTemplate();
        settingRepository.put(TEMPLATE_KEY, template, clock.instant(), chosenBy.staffId(), chosenBy.name());

        log.info("Template changed from {} to {} by {} (staff ID {})", before, template, chosenBy.name(), chosenBy.staffId());
        return template;
    }
}
