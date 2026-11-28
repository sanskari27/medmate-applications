package in.medmate.pharmacy_server.customer.web;

import java.util.List;

public final class PharmacyShelfViews {

    private PharmacyShelfViews() {}

    public record Item(
            String productId,
            String name,
            String displayName,
            String image,
            boolean rxRequired,
            String pack,
            String category,
            String onlineMrp,
            String onlinePrice) {}

    public record Category(String name, int total) {}

    public record Shelf(List<Category> categories, List<Item> items, int page, int size, int total) {}
}
