package in.medmate.pharmacy_server.prep;

import in.medmate.pharmacy_server.identity.access.Access;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;

public final class PrepAccess {

    private PrepAccess() {}

    public static boolean open(AccountPrincipal principal) {
        if (principal == null) {
            return false;
        }
        return Access.platform(principal.accountType(), principal.disabled(), principal.subtypes(), "sales");
    }
}
