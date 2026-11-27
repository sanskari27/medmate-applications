package in.medmate.pharmacy_server.customer.web;

import in.medmate.pharmacy_server.identity.web.Current;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "prod", "identity-it"})
public class NearbyController {

    private final NearbyService nearby;

    public NearbyController(NearbyService nearby) {
        this.nearby = nearby;
    }

    @GetMapping("/api/v1/customer/pharmacies/nearby")
    public NearbyViews.ListResponse nearby(
            Authentication authentication,
            @RequestParam(required = false) String latitude,
            @RequestParam(required = false) String longitude,
            @RequestParam(required = false) String limit) {
        return nearby.list(Current.require(authentication), latitude, longitude, limit);
    }
}
