package in.medmate.pharmacy_server.coupons;

import in.medmate.pharmacy_server.customer.CustomerAccess;
import in.medmate.pharmacy_server.identity.access.Access;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;

public final class CouponAccess {

    private CouponAccess() {}

    public static boolean manage(AccountPrincipal principal) {
        if (principal == null) {
            return false;
        }
        return Access.platform(principal.accountType(), principal.disabled(), principal.subtypes(), "sales");
    }

    public static boolean customer(AccountPrincipal principal) {
        return CustomerAccess.allowed(principal);
    }
}
