package in.medmate.pharmacy_server.customer.web;

import in.medmate.pharmacy_server.catalog.CatalogDocuments;
import in.medmate.pharmacy_server.catalog.CatalogStore;
import in.medmate.pharmacy_server.customer.AvailableCatalogRules;
import in.medmate.pharmacy_server.customer.CatalogOfferRules;
import in.medmate.pharmacy_server.customer.CustomerAccess;
import in.medmate.pharmacy_server.customer.DeliveryChargePolicy;
import in.medmate.pharmacy_server.customer.NearbyRules;
import in.medmate.pharmacy_server.customer.store.AddressStore;
import in.medmate.pharmacy_server.customer.store.NearbyStore;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.shared.error.ApiProblem;
import in.medmate.pharmacy_server.shared.web.PageQuery;
import in.medmate.pharmacy_server.stock.store.StockStore;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
    private final AddressStore addresses;
    private final NearbyStore nearby;
    private final Clock clock;

    public CustomerCatalogService(
            CatalogStore catalog, StockStore stock, AddressStore addresses, NearbyStore nearby, Clock clock) {
        this.catalog = catalog;
        this.stock = stock;
        this.addresses = addresses;
        this.nearby = nearby;
        this.clock = clock;
    }

    public CustomerCatalogViews.Page list(
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
        CatalogDocuments.AvailablePage pageResult =
                catalog.available(available, browse.category(), browse.query(), window.page(), window.size());
        Map<String, CatalogOfferRules.Offer> offers = offers(actor, pageResult.items(), month);
        List<CustomerCatalogViews.Item> items = new ArrayList<>();
        for (CatalogDocuments.AvailableItem item : pageResult.items()) {
            items.add(item(item, offers.get(item.productId())));
        }
        return new CustomerCatalogViews.Page(items, pageResult.page(), pageResult.size(), pageResult.total());
    }

    private Map<String, CatalogOfferRules.Offer> offers(
            AccountPrincipal actor, List<CatalogDocuments.AvailableItem> items, String month) {
        if (items.isEmpty()) {
            return Map.of();
        }
        AddressStore.AddressRow address = addresses.selected(actor.id()).orElse(null);
        if (address == null) {
            return Map.of();
        }
        List<NearbyStore.NearbyRow> pharmacies =
                nearby.within(address.latitude(), address.longitude(), NearbyRules.MAX_DISTANCE_KM);
        if (pharmacies.isEmpty()) {
            return Map.of();
        }
        List<UUID> pharmacyIds = new ArrayList<>();
        List<CatalogOfferRules.Pharmacy> candidates = new ArrayList<>();
        for (NearbyStore.NearbyRow pharmacy : pharmacies) {
            pharmacyIds.add(pharmacy.pharmacyId());
            candidates.add(
                    new CatalogOfferRules.Pharmacy(pharmacy.pharmacyId(), pharmacy.name(), pharmacy.distanceKm()));
        }
        List<String> productIds = new ArrayList<>();
        for (CatalogDocuments.AvailableItem item : items) {
            productIds.add(item.productId());
        }
        List<CatalogOfferRules.Listing> listings = new ArrayList<>();
        for (StockStore.OnlineListing listing : stock.onlineListings(pharmacyIds, productIds, month)) {
            listings.add(new CatalogOfferRules.Listing(
                    listing.pharmacyId(),
                    listing.productId(),
                    listing.onlineMrp(),
                    listing.onlinePrice(),
                    listing.availableQty()));
        }
        return CatalogOfferRules.nearest(candidates, listings);
    }

    private static CustomerCatalogViews.Item item(CatalogDocuments.AvailableItem item, CatalogOfferRules.Offer offer) {
        if (offer == null) {
            return new CustomerCatalogViews.Item(
                    item.productId(),
                    item.name(),
                    item.displayName(),
                    item.price(),
                    item.image(),
                    item.rxRequired(),
                    item.marketerName(),
                    item.pack(),
                    item.productForm(),
                    item.composition(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null);
        }
        DeliveryChargePolicy.Quote delivery = DeliveryChargePolicy.quote(offer.distanceKm());
        return new CustomerCatalogViews.Item(
                item.productId(),
                item.name(),
                item.displayName(),
                item.price(),
                item.image(),
                item.rxRequired(),
                item.marketerName(),
                item.pack(),
                item.productForm(),
                item.composition(),
                offer.pharmacyId(),
                offer.pharmacyName(),
                offer.distanceKm(),
                money(offer.onlineMrp()),
                money(offer.onlinePrice()),
                new CustomerCatalogViews.DeliveryCharge(money(delivery.amount()), delivery.distanceKm()));
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
