package in.medmate.pharmacy_server.customer.web;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.customer.AddressRules;
import in.medmate.pharmacy_server.customer.CustomerAccess;
import in.medmate.pharmacy_server.customer.store.AddressStore;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.shared.error.ApiProblem;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "prod", "identity-it"})
public class AddressService {

    private final AddressStore addresses;
    private final Clock clock;

    public AddressService(AddressStore addresses, Clock clock) {
        this.addresses = addresses;
        this.clock = clock;
    }

    public AddressViews.ListResponse list(AccountPrincipal actor) {
        requireCustomer(actor);
        return new AddressViews.ListResponse(views(addresses.list(actor.id())));
    }

    @Transactional
    public AddressViews.OneResponse create(AccountPrincipal actor, JsonNode body) {
        requireCustomer(actor);
        if (addresses.count(actor.id()) >= AddressRules.MAX_ADDRESSES) {
            throw new ApiProblem(HttpStatus.CONFLICT, "Conflict", "You can save up to 10 addresses");
        }
        AddressRules.Prepared prepared = AddressRules.prepareCreate(body);
        if (prepared.problem() != null) {
            throw bad(prepared.problem());
        }
        if (prepared.values().selected()) {
            addresses.clearSelected(actor.id());
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        addresses.insert(id, actor.id(), prepared.values(), now);
        return one(actor.id(), id);
    }

    @Transactional
    public AddressViews.OneResponse update(AccountPrincipal actor, UUID id, JsonNode body) {
        requireCustomer(actor);
        AddressStore.AddressRow current = addresses.find(actor.id(), id).orElseThrow(AddressService::missing);
        AddressRules.Prepared prepared = AddressRules.prepareUpdate(body, values(current));
        if (prepared.problem() != null) {
            throw bad(prepared.problem());
        }
        if (prepared.values().selected()) {
            addresses.clearSelected(actor.id());
        }
        addresses.update(id, actor.id(), prepared.values(), clock.instant());
        return one(actor.id(), id);
    }

    @Transactional
    public AddressViews.OneResponse select(AccountPrincipal actor, UUID id) {
        requireCustomer(actor);
        addresses.find(actor.id(), id).orElseThrow(AddressService::missing);
        addresses.clearSelected(actor.id());
        addresses.markSelected(actor.id(), id, clock.instant());
        return one(actor.id(), id);
    }

    private AddressViews.OneResponse one(UUID accountId, UUID id) {
        return new AddressViews.OneResponse(view(addresses.find(accountId, id).orElseThrow(AddressService::missing)));
    }

    private List<AddressViews.AddressView> views(List<AddressStore.AddressRow> rows) {
        return rows.stream().map(AddressService::view).toList();
    }

    private static AddressViews.AddressView view(AddressStore.AddressRow row) {
        return new AddressViews.AddressView(
                row.id(),
                row.label(),
                row.line1(),
                row.line2(),
                row.landmark(),
                row.city(),
                row.pincode(),
                row.latitude(),
                row.longitude(),
                row.receiverName(),
                row.receiverPhone(),
                row.callOnArrival(),
                row.leaveWithSecurity(),
                row.dontRingBell(),
                row.liftNotWorking(),
                row.riderNote(),
                row.selected(),
                iso(row.createdAt()),
                iso(row.updatedAt()));
    }

    private static AddressRules.Values values(AddressStore.AddressRow row) {
        return new AddressRules.Values(
                row.label(),
                row.line1(),
                row.line2(),
                row.landmark(),
                row.city(),
                row.pincode(),
                row.latitude(),
                row.longitude(),
                row.receiverName(),
                row.receiverPhone(),
                row.callOnArrival(),
                row.leaveWithSecurity(),
                row.dontRingBell(),
                row.liftNotWorking(),
                row.riderNote(),
                row.selected());
    }

    private static String iso(Instant instant) {
        return DateTimeFormatter.ISO_INSTANT.format(instant);
    }

    private void requireCustomer(AccountPrincipal actor) {
        if (actor == null) {
            throw new ApiProblem(HttpStatus.UNAUTHORIZED, "Unauthorized", "Sign in is required");
        }
        if (!CustomerAccess.allowed(actor)) {
            throw new ApiProblem(HttpStatus.FORBIDDEN, "Forbidden", "This action is not allowed.");
        }
    }

    private static ApiProblem bad(String detail) {
        return new ApiProblem(HttpStatus.BAD_REQUEST, "Bad request", detail);
    }

    private static ApiProblem missing() {
        return new ApiProblem(HttpStatus.NOT_FOUND, "Not found", "Address was not found");
    }
}
