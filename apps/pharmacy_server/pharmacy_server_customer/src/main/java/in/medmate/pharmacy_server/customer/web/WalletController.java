package in.medmate.pharmacy_server.customer.web;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.identity.web.Current;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "prod", "identity-it"})
public class WalletController {

    private final WalletService wallets;

    public WalletController(WalletService wallets) {
        this.wallets = wallets;
    }

    @GetMapping("/api/v1/customer/wallet")
    public WalletViews.WalletResponse read(Authentication authentication) {
        return wallets.read(Current.require(authentication));
    }

    @GetMapping("/api/v1/customer/wallet/entries")
    public WalletViews.EntriesResponse entries(Authentication authentication) {
        return wallets.entries(Current.require(authentication));
    }

    @PostMapping("/api/v1/customer/wallet/referral")
    public WalletViews.WalletResponse link(Authentication authentication, @RequestBody JsonNode body) {
        return wallets.link(Current.require(authentication), body);
    }

    @PostMapping("/api/v1/customer/wallet/quote")
    public WalletViews.QuoteResponse quote(Authentication authentication, @RequestBody JsonNode body) {
        return wallets.quote(Current.require(authentication), body);
    }
}
