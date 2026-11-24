package in.medmate.pharmacy_server.catalog;

import in.medmate.pharmacy_server.identity.access.Access;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;

public final class CatalogAccess {

    private CatalogAccess() {}

    public static boolean write(AccountPrincipal principal) {
        if (principal == null) {
            return false;
        }
        return Access.platform(principal.accountType(), principal.disabled(), principal.subtypes(), "sales");
    }
}
