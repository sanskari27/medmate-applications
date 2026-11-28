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
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
    static final int SIMILAR_LIMIT = 8;

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

    public CustomerCatalogViews.Detail product(AccountPrincipal actor, String productId) {
        if (!CustomerAccess.allowed(actor)) {
            throw new ApiProblem(HttpStatus.FORBIDDEN, "Forbidden", "This action is not allowed.");
        }
        String id = productId == null ? "" : productId.trim();
        if (id.isEmpty()) {
            throw missing();
        }
        String month = YearMonth.now(clock.withZone(ZONE)).toString();
        if (!new HashSet<>(stock.availableProductIds(month)).contains(id)) {
            throw missing();
        }
        CatalogStore.Found found = catalog.findMany(List.of(id));
        if (!found.drug().isEmpty()) {
            return drug(actor, found.drug().getFirst(), month);
        }
        if (!found.fmcg().isEmpty()) {
            return fmcg(actor, found.fmcg().getFirst(), month);
        }
        throw missing();
    }

    private CustomerCatalogViews.Detail drug(
            AccountPrincipal actor, CatalogDocuments.DrugDetail drug, String month) {
        String category = firstCategory(drug.categories());
        return new CustomerCatalogViews.Detail(
                drug.productId(),
                drug.productName(),
                text(drug.displayName()),
                text(drug.marketerName()),
                pack(drug.packageName(), drug.packagingDetail()),
                text(drug.productForm()),
                composition(drug.ingredients()),
                drug.prescriptionRequired(),
                texts(drug.categories()),
                texts(drug.images()),
                text(drug.introduction()),
                text(drug.benefits()),
                text(drug.howToUse()),
                text(drug.ifMiss()),
                text(drug.storage()),
                moneyOrNull(drug.mrp()),
                texts(drug.uses()),
                texts(drug.sideEffects()),
                drugSafety(drug.safety()),
                questions(drug.questions()),
                offersFor(actor, drug.productId(), month),
                similar(actor, drug.productId(), category, month));
    }

    private CustomerCatalogViews.Detail fmcg(
            AccountPrincipal actor, CatalogDocuments.FmcgDetail item, String month) {
        String category = firstCategory(item.categories());
        return new CustomerCatalogViews.Detail(
                item.productId(),
                item.productName(),
                text(item.displayName()),
                text(item.marketerName()),
                pack(item.packageName(), item.packaging()),
                text(item.productForm()),
                joined(item.ingredients(), " + "),
                false,
                texts(item.categories()),
                texts(item.images()),
                text(item.information()),
                joined(item.benefits(), "\n"),
                text(item.directionsForUse()),
                null,
                null,
                moneyOrNull(item.mrp()),
                texts(item.highlights()),
                List.of(),
                fmcgSafety(item.safety()),
                List.of(),
                offersFor(actor, item.productId(), month),
                similar(actor, item.productId(), category, month));
    }

    private List<CustomerCatalogViews.Offer> offersFor(AccountPrincipal actor, String productId, String month) {
        AddressStore.AddressRow address = addresses.selected(actor.id()).orElse(null);
        if (address == null || address.latitude() == null || address.longitude() == null) {
            return List.of();
        }
        List<NearbyStore.NearbyRow> pharmacies = nearby.nearby(
                address.latitude(), address.longitude(), NearbyRules.MAX_LIMIT, NearbyRules.MAX_DISTANCE_KM);
        List<UUID> pharmacyIds = new ArrayList<>();
        for (NearbyStore.NearbyRow pharmacy : pharmacies) {
            pharmacyIds.add(pharmacy.pharmacyId());
        }
        Map<UUID, StockStore.OnlineListing> listings = new LinkedHashMap<>();
        for (StockStore.OnlineListing listing : stock.onlineListings(pharmacyIds, List.of(productId), month)) {
            if (!productId.equals(listing.productId()) || listing.availableQty() <= 0) {
                continue;
            }
            listings.putIfAbsent(listing.pharmacyId(), listing);
        }
        List<CustomerCatalogViews.Offer> offers = new ArrayList<>();
        for (NearbyStore.NearbyRow pharmacy : pharmacies) {
            if (!pharmacy.acceptOnlineOrders()) {
                continue;
            }
            StockStore.OnlineListing listing = listings.get(pharmacy.pharmacyId());
            if (listing == null) {
                continue;
            }
            DeliveryChargePolicy.Quote delivery = DeliveryChargePolicy.quote(pharmacy.distanceKm());
            NearbyRules.HoursNow hours = NearbyRules.today(pharmacy.openingHours(), clock.instant());
            offers.add(new CustomerCatalogViews.Offer(
                    pharmacy.pharmacyId(),
                    pharmacy.name(),
                    pharmacy.distanceKm(),
                    money(listing.onlineMrp()),
                    money(listing.onlinePrice()),
                    new CustomerCatalogViews.DeliveryCharge(money(delivery.amount()), delivery.distanceKm()),
                    text(pharmacy.licenceNumber()),
                    text(pharmacy.pharmacistName()),
                    text(pharmacy.city()),
                    hours.closed() ? null : text(hours.closesOn()),
                    hours.closed()));
        }
        offers.sort(Comparator.comparingDouble(CustomerCatalogViews.Offer::distanceKm)
                .thenComparing(CustomerCatalogViews.Offer::pharmacyId));
        return offers;
    }

    private List<CustomerCatalogViews.Item> similar(
            AccountPrincipal actor, String productId, String category, String month) {
        if (category.isEmpty()) {
            return List.of();
        }
        CatalogDocuments.AvailablePage page = catalog.available(
                stock.availableProductIds(month), category, "", 1, SIMILAR_LIMIT + 1);
        List<CatalogDocuments.AvailableItem> picked = new ArrayList<>();
        for (CatalogDocuments.AvailableItem item : page.items()) {
            if (productId.equals(item.productId())) {
                continue;
            }
            picked.add(item);
            if (picked.size() == SIMILAR_LIMIT) {
                break;
            }
        }
        Map<String, CatalogOfferRules.Offer> nearest = offers(actor, picked, month);
        List<CustomerCatalogViews.Item> items = new ArrayList<>();
        for (CatalogDocuments.AvailableItem item : picked) {
            items.add(item(item, nearest.get(item.productId())));
        }
        return items;
    }

    private static ApiProblem missing() {
        return new ApiProblem(HttpStatus.NOT_FOUND, "Not found", "Product was not found");
    }

    private static String firstCategory(List<String> categories) {
        for (String category : texts(categories)) {
            return category;
        }
        return "";
    }

    private static String composition(List<CatalogDocuments.DrugIngredient> ingredients) {
        if (ingredients == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (CatalogDocuments.DrugIngredient ingredient : ingredients) {
            if (ingredient == null) {
                continue;
            }
            String name = text(ingredient.name());
            if (name == null) {
                continue;
            }
            String strength = text(ingredient.strength());
            parts.add(strength == null ? name : name + " (" + strength + ")");
        }
        return joined(parts, " + ");
    }

    private static List<CustomerCatalogViews.SafetyNote> drugSafety(List<CatalogDocuments.DrugSafety> safety) {
        if (safety == null) {
            return List.of();
        }
        List<CustomerCatalogViews.SafetyNote> notes = new ArrayList<>();
        for (CatalogDocuments.DrugSafety note : safety) {
            if (note == null || text(note.detail()) == null) {
                continue;
            }
            notes.add(new CustomerCatalogViews.SafetyNote(text(note.topic()), text(note.level()), text(note.detail())));
        }
        return List.copyOf(notes);
    }

    private static List<CustomerCatalogViews.SafetyNote> fmcgSafety(List<String> safety) {
        List<CustomerCatalogViews.SafetyNote> notes = new ArrayList<>();
        for (String line : texts(safety)) {
            notes.add(new CustomerCatalogViews.SafetyNote(null, null, line));
        }
        return List.copyOf(notes);
    }

    private static List<CustomerCatalogViews.Question> questions(List<CatalogDocuments.DrugQuestion> questions) {
        if (questions == null) {
            return List.of();
        }
        List<CustomerCatalogViews.Question> copy = new ArrayList<>();
        for (CatalogDocuments.DrugQuestion question : questions) {
            if (question == null || text(question.question()) == null || text(question.answer()) == null) {
                continue;
            }
            copy.add(new CustomerCatalogViews.Question(text(question.question()), text(question.answer())));
        }
        return List.copyOf(copy);
    }

    private static String pack(String packageName, String packaging) {
        String named = text(packageName);
        if (named != null) {
            return named;
        }
        return text(packaging);
    }

    private static List<String> texts(List<String> values) {
        if (values == null) {
            return List.of();
        }
        List<String> copy = new ArrayList<>();
        for (String value : values) {
            String clean = text(value);
            if (clean != null) {
                copy.add(clean);
            }
        }
        return List.copyOf(copy);
    }

    private static String joined(List<String> values, String separator) {
        List<String> clean = texts(values);
        if (clean.isEmpty()) {
            return null;
        }
        return String.join(separator, clean);
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String moneyOrNull(BigDecimal value) {
        if (value == null) {
            return null;
        }
        return money(value);
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
