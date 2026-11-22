package com.notapos.security;

/**
 * Who may do what: the role matrix, in one place.
 *
 * WHAT: Each constant is a rule, written in the small expression language
 *       Spring Security reads inside @PreAuthorize. A controller method names
 *       the rule that applies to it:
 *
 *           @PreAuthorize(Permissions.TAKE_ORDERS)
 *           @PostMapping
 *           public ResponseEntity<OrderItem> ringInItem(...)
 *
 *       hasRole('MANAGER') is true when the signed-in person's role is manager.
 *       hasAnyRole(...) is true when it is any one of the roles listed.
 *
 * WHY:  The same list of roles applies to many endpoints (nine of them are
 *       "takes orders"). Typed out at each one, the lists would drift apart
 *       the first time a role is added. Here a row of the matrix is changed
 *       once.
 *
 * THIS FILE IS THE SOURCE. The frontend's utils/roles.js repeats part of it so
 * that screens do not offer a button the backend would refuse. When the two
 * disagree, this one is right and roles.js is the one to correct.
 *
 * WHAT HAS NO RULE: an endpoint with no @PreAuthorize is open to anyone who is
 * signed in (SecurityConfig refuses everyone else before the controller is
 * reached). That is right for reading the menu, tables, and orders, and for
 * nothing that changes data: RoleMatrixIntegrationTest fails if a POST, PUT, or
 * DELETE endpoint has no rule, and lists every endpoint with the roles it
 * expects.
 *
 * The matrix was decided by the owner on 2026-11-20 (decision D2 in
 * Documentation/phase-2-plan.md).
 *
 * @author CJ
 */
public final class Permissions {

    // Open orders, ring in, send, take payment, clear an empty check, start a Quick Order
    public static final String TAKE_ORDERS = "hasAnyRole('SERVER', 'BARTENDER', 'HOST', 'MANAGER')";

    // Start and complete items on the kitchen display
    public static final String MAKE_ORDERS = "hasAnyRole('KITCHEN', 'BARTENDER', 'MANAGER')";

    // 86 an item and put it back on. The people who know what ran out.
    public static final String CHANGE_86 = "hasAnyRole('KITCHEN', 'BARTENDER', 'MANAGER')";

    // The menu (items, modifiers, prep stations), staff, the Financials report, the floor
    // layout, and reading payments and tips. Also every endpoint that changes data which
    // the matrix does not name and no screen uses yet: closed until a feature needs it.
    public static final String MANAGER_ONLY = "hasRole('MANAGER')";

    // The search box in the top bar: every role. It finds tables, checks, and menu items,
    // which every role may already read. What a closed check in the answer says about its
    // tip is decided per person in the service, as it is on the Tables screen.
    public static final String SEARCH = "isAuthenticated()";

    // Reading and dismissing your own notifications (the bell in the top bar): every role.
    // "Your own" is not a role question, so it is not decided here: the endpoints take no
    // staff ID at all, and NotificationService only ever asks for the signed-in person's.
    public static final String OWN_NOTIFICATIONS = "isAuthenticated()";

    // Writing a message to the floor: the kitchen, bartenders, and managers (the owner's
    // decision D5). The same roles as CHANGE_86 today, and a rule of its own because it is
    // a different job: one may change without the other.
    public static final String SEND_MESSAGES = "hasAnyRole('KITCHEN', 'BARTENDER', 'MANAGER')";

    // Reading what each person sold in a period (the leaderboard): every role. The owner's
    // decision D6: everyone sees everyone's name, rank, and sales. It carries no tips; tips
    // stay with the person who earned them and managers (D10), and that is kept by the
    // answer having no tip in it, not by this rule. The same roles as SEARCH today, and a
    // rule of its own because it is a different job.
    public static final String SEE_STAFF_SALES = "isAuthenticated()";

    // Any role at all. Not needed to let people in (a method with no rule is already open to
    // everyone signed in); it is written on the few endpoints that change something and are
    // meant for every role, to say that the missing role list is on purpose.
    public static final String ANYONE_SIGNED_IN = "isAuthenticated()";

    private Permissions() {
        // Constants only; never created
    }
}
