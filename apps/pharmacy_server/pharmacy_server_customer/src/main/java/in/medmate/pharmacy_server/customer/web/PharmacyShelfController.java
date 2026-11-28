package in.medmate.pharmacy_server.customer.web;

import in.medmate.pharmacy_server.identity.web.Current;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "prod", "identity-it"})
@ConditionalOnProperty(prefix = "medmate.catalog", name = "url")
public class PharmacyShelfController {

    private final PharmacyShelfService shelf;

    public PharmacyShelfController(PharmacyShelfService shelf) {
        this.shelf = shelf;
    }

    @GetMapping("/api/v1/customer/pharmacies/{pharmacyId}/catalog")
    public PharmacyShelfViews.Shelf catalog(
            Authentication authentication,
            @PathVariable String pharmacyId,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String rx,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        return shelf.catalog(Current.require(authentication), pharmacyId, q, rx, category, page, size);
    }
}
