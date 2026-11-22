package com.notapos.enums;

/**
 * How far back the search box looks for CLOSED checks.
 *
 * WHAT: Four choices. Each is "from the start of a day until now", by the
 *       backend's own calendar: TODAY is since midnight, YESTERDAY is since
 *       the midnight before that (so it includes today), and so on.
 * WHY:  Most searches for a paid check are about the shift in progress ("did
 *       table 7 pay?"), so today is the default and the search stays quick.
 *       Looking further back is a choice the person makes.
 *
 * The value is what the request carries: GET /api/search?q=...&closed=7days
 *
 * @author CJ
 */
public enum SearchPeriod {
    TODAY("today", 0),
    YESTERDAY("yesterday", 1),
    LAST_7_DAYS("7days", 6),
    LAST_30_DAYS("30days", 29);

    private final String value;
    private final int daysBeforeToday;

    SearchPeriod(String value, int daysBeforeToday) {
        this.value = value;
        this.daysBeforeToday = daysBeforeToday;
    }

    /** What the request carries for this choice, e.g. "7days". */
    public String getValue() {
        return value;
    }

    /** How many whole days before today the period starts (0 for today alone). */
    public int getDaysBeforeToday() {
        return daysBeforeToday;
    }

    /**
     * The period a request named.
     *
     * @throws IllegalArgumentException if it is not one of the four
     */
    public static SearchPeriod fromValue(String value) {
        for (SearchPeriod period : values()) {
            if (period.value.equals(value)) {
                return period;
            }
        }
        throw new IllegalArgumentException("Not a search period: " + value);
    }
}
