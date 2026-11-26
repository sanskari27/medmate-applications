package in.medmate.pharmacy_server.customer.web;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.identity.web.Current;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "prod", "identity-it"})
public class AddressController {

    private final AddressService addresses;

    public AddressController(AddressService addresses) {
        this.addresses = addresses;
    }

    @GetMapping("/api/v1/customer/addresses")
    public AddressViews.ListResponse list(Authentication authentication) {
        return addresses.list(Current.require(authentication));
    }

    @PostMapping("/api/v1/customer/addresses")
    public ResponseEntity<AddressViews.OneResponse> create(Authentication authentication, @RequestBody JsonNode body) {
        return ResponseEntity.status(HttpStatus.CREATED).body(addresses.create(Current.require(authentication), body));
    }

    @PatchMapping("/api/v1/customer/addresses/{id}")
    public AddressViews.OneResponse update(
            Authentication authentication, @PathVariable UUID id, @RequestBody JsonNode body) {
        return addresses.update(Current.require(authentication), id, body);
    }

    @PostMapping("/api/v1/customer/addresses/{id}/select")
    public AddressViews.OneResponse select(Authentication authentication, @PathVariable UUID id) {
        return addresses.select(Current.require(authentication), id);
    }
}
