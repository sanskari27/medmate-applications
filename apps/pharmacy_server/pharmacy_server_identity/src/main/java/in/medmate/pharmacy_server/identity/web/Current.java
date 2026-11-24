package in.medmate.pharmacy_server.identity.web;

import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.shared.error.ApiProblem;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

public final class Current {

    private Current() {}

    public static AccountPrincipal require(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof AccountPrincipal principal) {
            return principal;
        }
        throw new ApiProblem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Sign in is required");
    }
}
