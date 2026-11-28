package in.medmate.pharmacy_server.customer.web;

import in.medmate.pharmacy_server.catalog.CatalogDocuments;
import in.medmate.pharmacy_server.catalog.CatalogStore;
import in.medmate.pharmacy_server.customer.CustomerAccess;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile({"local", "prod", "identity-it"})
@ConditionalOnProperty(prefix = "medmate.catalog", name = "url")
public class PharmacyShelfService {

    static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    static final int LIST_PREVIEW = 3;
    static final int GRID_PREVIEW = 9;
    static final String EVERYDAY = "everyday essentials";
    static final String OTHER = "Other";

    private final CatalogStore catalog;
    private final StockStore stock;
    private final AddressStore addresses;
    private final NearbyStore nearby;
    private final Clock clock;

    public PharmacyShelfService(
            CatalogStore catalog, StockStore stock, AddressStore addresses, NearbyStore nearby, Clock clock) {
        this.catalog = catalog;
        this.stock = stock;
        this.addresses = addresses;
        this.nearby = nearby;
        this.clock = clock;
    }

    public PharmacyShelfViews.Shelf catalog(
            AccountPrincipal actor,
            String pharmacyId,
            String query,
            String rx,
            String category,
            String page,
            String size) {
        if (!CustomerAccess.allowed(actor)) {
            throw problem(HttpStatus.FORBIDDEN, "This action is not allowed.");
        }
        String prescription = prescription(rx);
        UUID id = pharmacyId(pharmacyId);
        requireOnline(actor, id);
        String month = YearMonth.now(clock.withZone(ZONE)).toString();
        Map<String, StockStore.OnlineListing> listings = listings(id, month);
        if (listings.isEmpty()) {
            return empty(category, page, size);
        }
        List<String> productIds = new ArrayList<>(listings.keySet());
        String needle = query == null ? "" : query.trim();
        CatalogDocuments.AvailablePage found = catalog.available(productIds, "", needle, 1, productIds.size());
        Map<String, String> categories = categories(productIds);
        List<Group> groups = groups(found.items(), listings, categories, prescription);
        String named = category == null ? "" : category.trim();
        if (named.isEmpty()) {
            List<PharmacyShelfViews.Item> preview = preview(groups);
            return new PharmacyShelfViews.Shelf(names(groups), preview, 1, preview.size(), count(groups));
        }
        PageQuery window = PageQuery.parse(page, size);
        Group group = group(groups, named);
        if (group == null) {
            return new PharmacyShelfViews.Shelf(names(groups), List.of(), window.page(), window.size(), 0);
        }
        return new PharmacyShelfViews.Shelf(
                names(groups), page(group, window), window.page(), window.size(), group.items.size());
    }

    private PharmacyShelfViews.Shelf empty(String category, String page, String size) {
        String named = category == null ? "" : category.trim();
        if (named.isEmpty()) {
            return new PharmacyShelfViews.Shelf(List.of(), List.of(), 1, 0, 0);
        }
        PageQuery window = PageQuery.parse(page, size);
        return new PharmacyShelfViews.Shelf(List.of(), List.of(), window.page(), window.size(), 0);
    }

    private void requireOnline(AccountPrincipal actor, UUID pharmacyId) {
        AddressStore.AddressRow address = addresses.selected(actor.id()).orElse(null);
        if (address == null || address.latitude() == null || address.longitude() == null) {
            throw missing();
        }
        for (NearbyStore.NearbyRow pharmacy : nearby.nearby(
                address.latitude(), address.longitude(), NearbyRules.MAX_LIMIT, NearbyRules.MAX_DISTANCE_KM)) {
            if (pharmacy.pharmacyId().equals(pharmacyId) && pharmacy.acceptOnlineOrders()) {
                return;
            }
        }
        throw missing();
    }

    private Map<String, StockStore.OnlineListing> listings(UUID pharmacyId, String month) {
        Map<String, StockStore.OnlineListing> listings = new LinkedHashMap<>();
        for (StockStore.OnlineListing listing : stock.pharmacyListings(pharmacyId, month)) {
            if (listing.onlineMrp() == null || listing.onlinePrice() == null || listing.availableQty() <= 0) {
                continue;
            }
            listings.putIfAbsent(listing.productId(), listing);
        }
        return listings;
    }

    private Map<String, String> categories(List<String> productIds) {
        Map<String, String> categories = new LinkedHashMap<>();
        for (CatalogDocuments.ProductCategory category : catalog.primaryCategories(productIds)) {
            String name = text(category.category());
            if (name == null) {
                continue;
            }
            categories.putIfAbsent(category.productId(), name);
        }
        return categories;
    }

    private static List<Group> groups(
            List<CatalogDocuments.AvailableItem> items,
            Map<String, StockStore.OnlineListing> listings,
            Map<String, String> categories,
            String prescription) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (CatalogDocuments.AvailableItem item : items) {
            StockStore.OnlineListing listing = listings.get(item.productId());
            if (listing == null || !matches(item.rxRequired(), prescription)) {
                continue;
            }
            String name = categories.getOrDefault(item.productId(), OTHER);
            String key = name.toLowerCase(Locale.ROOT);
            Group group = groups.get(key);
            if (group == null) {
                group = new Group(name);
                groups.put(key, group);
            }
            group.items.add(itemOf(item, listing, group.name));
        }
        List<Group> ordered = new ArrayList<>(groups.values());
        ordered.sort(Comparator.comparingInt((Group group) -> rank(group.name))
                .thenComparing(group -> group.name, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(group -> group.name));
        return ordered;
    }

    private static boolean matches(boolean rxRequired, String prescription) {
        if (prescription == null) {
            return true;
        }
        if ("required".equals(prescription)) {
            return rxRequired;
        }
        return !rxRequired;
    }

    private static PharmacyShelfViews.Item itemOf(
            CatalogDocuments.AvailableItem item, StockStore.OnlineListing listing, String category) {
        return new PharmacyShelfViews.Item(
                item.productId(),
                text(item.name()),
                text(item.displayName()),
                text(item.image()),
                item.rxRequired(),
                text(item.pack()),
                category,
                money(listing.onlineMrp()),
                money(listing.onlinePrice()));
    }

    private static List<PharmacyShelfViews.Item> preview(List<Group> groups) {
        List<PharmacyShelfViews.Item> items = new ArrayList<>();
        for (Group group : groups) {
            int limit = everyday(group.name) ? GRID_PREVIEW : LIST_PREVIEW;
            int count = Math.min(limit, group.items.size());
            items.addAll(group.items.subList(0, count));
        }
        return items;
    }

    private static List<PharmacyShelfViews.Item> page(Group group, PageQuery window) {
        int from = Math.min(window.offset(), group.items.size());
        int to = Math.min(from + window.size(), group.items.size());
        return List.copyOf(group.items.subList(from, to));
    }

    private static Group group(List<Group> groups, String category) {
        String key = category.toLowerCase(Locale.ROOT);
        for (Group group : groups) {
            if (group.name.toLowerCase(Locale.ROOT).equals(key)) {
                return group;
            }
        }
        return null;
    }

    private static List<PharmacyShelfViews.Category> names(List<Group> groups) {
        List<PharmacyShelfViews.Category> categories = new ArrayList<>();
        for (Group group : groups) {
            categories.add(new PharmacyShelfViews.Category(group.name, group.items.size()));
        }
        return categories;
    }

    private static int count(List<Group> groups) {
        int total = 0;
        for (Group group : groups) {
            total += group.items.size();
        }
        return total;
    }

    private static int rank(String name) {
        if (everyday(name)) {
            return 0;
        }
        if (OTHER.equalsIgnoreCase(name)) {
            return 2;
        }
        return 1;
    }

    private static boolean everyday(String name) {
        return EVERYDAY.equals(name.toLowerCase(Locale.ROOT));
    }

    private static String prescription(String rx) {
        if (rx == null || rx.isBlank()) {
            return null;
        }
        String value = rx.trim().toLowerCase(Locale.ROOT);
        if ("required".equals(value) || "otc".equals(value)) {
            return value;
        }
        throw problem(HttpStatus.BAD_REQUEST, "Prescription filter is not valid");
    }

    private static UUID pharmacyId(String raw) {
        if (raw == null || raw.isBlank()) {
            throw missing();
        }
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException exception) {
            throw missing();
        }
    }

    private static ApiProblem missing() {
        return problem(HttpStatus.NOT_FOUND, "Pharmacy was not found");
    }

    private static ApiProblem problem(HttpStatus status, String detail) {
        String title =
                switch (status) {
                    case BAD_REQUEST -> "Bad request";
                    case FORBIDDEN -> "Forbidden";
                    case NOT_FOUND -> "Not found";
                    default -> status.getReasonPhrase();
                };
        return new ApiProblem(status, title, detail);
    }

    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static final class Group {
        private final String name;
        private final List<PharmacyShelfViews.Item> items = new ArrayList<>();

        private Group(String name) {
            this.name = name;
        }
    }
}
