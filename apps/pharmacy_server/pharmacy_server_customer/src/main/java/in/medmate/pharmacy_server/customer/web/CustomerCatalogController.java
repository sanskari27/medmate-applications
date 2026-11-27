package in.medmate.pharmacy_server.customer.web;

import in.medmate.pharmacy_server.identity.web.Current;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "prod", "identity-it"})
@ConditionalOnProperty(prefix = "medmate.catalog", name = "url")
public class CustomerCatalogController {

    private final CustomerCatalogService catalog;

    public CustomerCatalogController(CustomerCatalogService catalog) {
        this.catalog = catalog;
    }

    @GetMapping("/api/v1/customer/catalog")
    public CustomerCatalogViews.Page list(
            Authentication authentication,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return catalog.list(Current.require(authentication), category, q, page, size);
    }
}
