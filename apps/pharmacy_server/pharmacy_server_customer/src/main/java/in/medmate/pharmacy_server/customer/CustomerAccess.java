package in.medmate.pharmacy_server.customer;

import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.identity.access.AccountType;

public final class CustomerAccess {

    private CustomerAccess() {}

    public static boolean allowed(AccountPrincipal principal) {
        return principal != null && !principal.disabled() && principal.accountType() == AccountType.CUSTOMER;
    }
}
