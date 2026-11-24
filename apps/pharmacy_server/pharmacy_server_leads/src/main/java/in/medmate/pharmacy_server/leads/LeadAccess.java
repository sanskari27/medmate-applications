package in.medmate.pharmacy_server.leads;

import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.identity.access.AccountType;
import in.medmate.pharmacy_server.identity.access.StaffSubtype;
import java.util.Set;

public final class LeadAccess {

    private LeadAccess() {}

    public static boolean read(AccountPrincipal principal) {
        if (principal == null || principal.disabled()) {
            return false;
        }
        if (principal.accountType() == AccountType.ADMIN) {
            return true;
        }
        if (principal.accountType() != AccountType.STAFF) {
            return false;
        }
        Set<StaffSubtype> subtypes = principal.subtypes();
        return subtypes != null && subtypes.contains(StaffSubtype.SUPPORT);
    }
}
