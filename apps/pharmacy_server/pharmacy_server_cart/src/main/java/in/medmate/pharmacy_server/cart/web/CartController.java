package in.medmate.pharmacy_server.cart.web;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.identity.web.Current;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "prod", "identity-it"})
@ConditionalOnProperty(prefix = "medmate.catalog", name = "url")
public class CartController {

    private final CartService cart;

    public CartController(CartService cart) {
        this.cart = cart;
    }

    @GetMapping("/api/v1/customer/cart")
    public CartViews.Cart get(Authentication authentication) {
        return cart.get(Current.require(authentication));
    }

    @PutMapping("/api/v1/customer/cart/lines")
    public CartViews.Cart saveLine(Authentication authentication, @RequestBody JsonNode body) {
        return cart.saveLine(Current.require(authentication), body);
    }

    @DeleteMapping("/api/v1/customer/cart/lines/{productId}")
    public CartViews.Cart deleteLine(Authentication authentication, @PathVariable String productId) {
        return cart.deleteLine(Current.require(authentication), productId);
    }

    @PutMapping("/api/v1/customer/cart/pharmacies")
    public CartViews.Cart savePharmacies(Authentication authentication, @RequestBody JsonNode body) {
        return cart.savePharmacies(Current.require(authentication), body);
    }

    @PutMapping("/api/v1/customer/cart/coupon")
    public CartViews.Cart saveCoupon(Authentication authentication, @RequestBody JsonNode body) {
        return cart.saveCoupon(Current.require(authentication), body);
    }

    @DeleteMapping("/api/v1/customer/cart/coupon")
    public CartViews.Cart clearCoupon(Authentication authentication) {
        return cart.clearCoupon(Current.require(authentication));
    }
}
