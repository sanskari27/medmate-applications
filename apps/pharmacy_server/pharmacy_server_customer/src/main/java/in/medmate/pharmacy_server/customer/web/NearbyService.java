package in.medmate.pharmacy_server.customer.web;

import in.medmate.pharmacy_server.customer.CustomerAccess;
import in.medmate.pharmacy_server.customer.NearbyRules;
import in.medmate.pharmacy_server.customer.store.NearbyStore;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.shared.error.ApiProblem;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile({"local", "prod", "identity-it"})
public class NearbyService {

    private final NearbyStore nearby;
    private final Clock clock;

    public NearbyService(NearbyStore nearby, Clock clock) {
        this.nearby = nearby;
        this.clock = clock;
    }

    public NearbyViews.ListResponse list(AccountPrincipal actor, String latitude, String longitude, String limit) {
        requireCustomer(actor);
        String problem = NearbyRules.problem(latitude, longitude, limit);
        if (problem != null) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "Bad request", problem);
        }
        NearbyRules.Query query = NearbyRules.query(latitude, longitude, limit);
        Instant now = clock.instant();
        List<NearbyViews.PharmacyView> pharmacies =
                nearby.nearby(query.latitude(), query.longitude(), query.limit(), NearbyRules.MAX_DISTANCE_KM).stream()
                        .map(row -> view(row, now))
                        .toList();
        return new NearbyViews.ListResponse(pharmacies);
    }

    private static NearbyViews.PharmacyView view(NearbyStore.NearbyRow row, Instant now) {
        NearbyRules.HoursNow today = NearbyRules.today(row.openingHours(), now);
        return new NearbyViews.PharmacyView(
                row.pharmacyId(),
                row.name(),
                row.address(),
                row.city(),
                row.state(),
                row.pincode(),
                row.latitude(),
                row.longitude(),
                Math.round(row.distanceKm() * 1000.0) / 1000.0,
                row.acceptOnlineOrders(),
                row.licenceNumber(),
                row.pharmacistName(),
                row.openingHours(),
                today.closed(),
                today.closesOn());
    }

    private static void requireCustomer(AccountPrincipal actor) {
        if (actor == null) {
            throw new ApiProblem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Sign in is required");
        }
        if (!CustomerAccess.allowed(actor)) {
            throw new ApiProblem(HttpStatus.FORBIDDEN, "Forbidden", "This action is not allowed.");
        }
    }
}
