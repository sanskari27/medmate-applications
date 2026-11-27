package in.medmate.pharmacy_server.customer.web;

import in.medmate.pharmacy_server.catalog.CatalogDocuments;
import in.medmate.pharmacy_server.catalog.CatalogStore;
import in.medmate.pharmacy_server.customer.AvailableCatalogRules;
import in.medmate.pharmacy_server.customer.CustomerAccess;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.shared.error.ApiProblem;
import in.medmate.pharmacy_server.shared.web.PageQuery;
import in.medmate.pharmacy_server.stock.store.StockStore;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile({"local", "prod", "identity-it"})
@ConditionalOnProperty(prefix = "medmate.catalog", name = "url")
public class CustomerCatalogService {

    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private final CatalogStore catalog;
    private final StockStore stock;
    private final Clock clock;

    public CustomerCatalogService(CatalogStore catalog, StockStore stock, Clock clock) {
        this.catalog = catalog;
        this.stock = stock;
        this.clock = clock;
    }

    public CatalogDocuments.AvailablePage list(
            AccountPrincipal actor, String category, String query, String page, String size) {
        if (!CustomerAccess.allowed(actor)) {
            throw new ApiProblem(HttpStatus.FORBIDDEN, "Forbidden", "This action is not allowed.");
        }
        AvailableCatalogRules.Browse browse = AvailableCatalogRules.browse(category, query);
        if (browse.problem() != null) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "Bad request", browse.problem());
        }
        PageQuery window = PageQuery.parse(page, size);
        String month = YearMonth.now(clock.withZone(ZONE)).toString();
        List<String> available = stock.availableProductIds(month);
        return catalog.available(available, browse.category(), browse.query(), window.page(), window.size());
    }
}
