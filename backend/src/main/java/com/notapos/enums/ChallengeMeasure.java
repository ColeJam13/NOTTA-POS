package com.notapos.enums;

import java.util.Arrays;
import java.util.Optional;

/**
 * What a challenge counts (the owner's decision D6: these four).
 *
 * WHAT: Each constant carries the exact lowercase text stored in the database
 *       and sent to the frontend, like the status enums (see OrderItemStatus).
 *
 * Every one of them is worked out, per person, from the checks CLOSED in the
 * challenge's period and OPENED by that person: the counting rule the
 * Financials report and the leaderboard use (see StaffSalesReportService).
 *
 * @author CJ
 */
public enum ChallengeMeasure {

    ITEMS_SOLD("items_sold"),            // Sell this many: of one menu category, or of one menu item
    TOTAL_SALES("total_sales"),          // Reach this much in sales (before tax)
    CHECK_AVERAGE("check_average"),      // Hold an average check of at least this, over a minimum number of checks
    BIGGEST_CHECK("biggest_check");      // Close one check of at least this

    private final String value;

    ChallengeMeasure(String value) {
        this.value = value;
    }

    public String getValue() {                    // The text stored in the database / sent as JSON
        return value;
    }

    public boolean matches(String measure) {      // Compare against the text an entity holds
        return value.equals(measure);
    }

    // The measure a piece of text names, if it names one ("total_sales" -> TOTAL_SALES)
    public static Optional<ChallengeMeasure> fromValue(String value) {
        return Arrays.stream(values()).filter(measure -> measure.value.equals(value)).findFirst();
    }
}
