package com.notapos.dto;

import java.util.List;

/**
 * One group of search results (tables, open checks, closed checks, or menu items).
 *
 * A group is capped (SearchService.GROUP_LIMIT). "more" says the cap was hit:
 * there are further matches that were not sent, so the screen can say "type
 * more to narrow it" where it would otherwise look as if this were everything.
 *
 * @param results The matches, best or newest first, at most the cap
 * @param more    True when there were more matches than the cap
 * @param <T>     What one result is
 */
public record SearchGroupView<T>(List<T> results, boolean more) {

    /** A group with nothing in it. */
    public static <T> SearchGroupView<T> empty() {
        return new SearchGroupView<>(List.of(), false);
    }
}
