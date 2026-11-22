package com.notapos.dto;

/**
 * Everything the search box shows for what was typed, in one response.
 *
 * Four groups, each capped. A group with no matches is an empty list, never
 * missing, so the screen has one shape to read.
 *
 * @param tables       Tables on the floor, by number (Quick Orders are checks, not tables)
 * @param openChecks   Open checks, by table number, check number, or who opened them
 * @param closedChecks Paid checks, by the same three, for the period asked for.
 *                     The same shape the Tables screen shows, and under the same
 *                     rule about tips (see ClosedCheckView).
 * @param menuItems    Items on the menu, by name
 */
public record SearchResultsView(
        SearchGroupView<SearchTableView> tables,
        SearchGroupView<SearchOpenCheckView> openChecks,
        SearchGroupView<ClosedCheckView> closedChecks,
        SearchGroupView<SearchMenuItemView> menuItems) {

    /** The answer when nothing was searched for (the text was too short). */
    public static SearchResultsView nothing() {
        return new SearchResultsView(SearchGroupView.empty(), SearchGroupView.empty(),
                SearchGroupView.empty(), SearchGroupView.empty());
    }
}
