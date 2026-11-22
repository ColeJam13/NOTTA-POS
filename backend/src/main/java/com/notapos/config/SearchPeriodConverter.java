package com.notapos.config;

import com.notapos.enums.SearchPeriod;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Reads the "closed" part of a search request (?closed=7days) into a SearchPeriod.
 *
 * WHY THIS EXISTS: Spring turns request text into an enum by itself, but only by
 * the constant's exact name (LAST_7_DAYS). The request carries the short value
 * (7days). A converter is the hook Spring offers for "read this text as that
 * type"; any @Component that implements it is picked up at start-up.
 *
 * A value that is not one of the four makes fromValue throw, and Spring answers
 * the request 400, the same as it does for a date it cannot read.
 *
 * @author CJ
 */
@Component
public class SearchPeriodConverter implements Converter<String, SearchPeriod> {

    @Override
    public SearchPeriod convert(String value) {
        return SearchPeriod.fromValue(value);
    }
}
