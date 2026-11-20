package com.notapos.dto;

import java.util.List;

/**
 * Everything the Tables screen (or the Quick Orders screen) shows, in one response.
 *
 * @param activeTables Tables with an open check, for the ACTIVE view
 * @param closedChecks The most recent paid checks, newest first, for the CLOSED view
 */
public record TablesOverviewView(
        List<ActiveTableView> activeTables,
        List<ClosedCheckView> closedChecks) {
}
