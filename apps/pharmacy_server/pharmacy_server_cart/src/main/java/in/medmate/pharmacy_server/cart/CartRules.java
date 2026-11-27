package in.medmate.pharmacy_server.cart;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CartRules {

    public static final int MAX_QTY = 99;
    public static final int MAX_LINES = 50;
    public static final String RECOMMENDED = "recommended";
    public static final String CHOSEN = "chosen";

    private CartRules() {}

    public record Line(String productId, int qty) {}

    public record Offer(
            UUID pharmacyId,
            String pharmacyName,
            double distanceKm,
            String productId,
            BigDecimal onlineMrp,
            BigDecimal onlinePrice,
            int availableQty) {}

    public record LinePlan(
            String productId,
            int qty,
            UUID assignedPharmacyId,
            BigDecimal onlineMrp,
            BigDecimal onlinePrice,
            BigDecimal lineTotal,
            boolean available,
            List<Offer> alternatives) {}

    public record PharmacyPlan(
            UUID pharmacyId,
            String name,
            Double distanceKm,
            boolean recommended,
            boolean selected,
            boolean eligible,
            List<String> productIds,
            List<String> assignedProductIds,
            BigDecimal merchandise) {}

    public record Arrangement(String selectionSource, List<LinePlan> lines, List<PharmacyPlan> pharmacies) {}

    public record LineChange(String productId, int qty, String problem) {}

    public record PharmacyChoice(List<UUID> pharmacyIds, String problem) {}

    public static LineChange line(JsonNode body) {
        if (body == null || !body.isObject()) {
            return new LineChange(null, 0, "Product is required");
        }
        JsonNode product = body.get("productId");
        String productId =
                product == null || product.isNull() ? "" : product.asText().trim();
        if (productId.isEmpty() || productId.length() > 80) {
            return new LineChange(null, 0, "Product is required");
        }
        JsonNode qty = body.get("qty");
        if (qty == null || !qty.isIntegralNumber()) {
            return new LineChange(null, 0, "Quantity must be a whole number");
        }
        int value = qty.intValue();
        if (value < 1 || value > MAX_QTY) {
            return new LineChange(null, 0, "Quantity must be from 1 to 99");
        }
        return new LineChange(productId, value, null);
    }

    public static PharmacyChoice pharmacies(JsonNode body) {
        if (body == null
                || !body.isObject()
                || body.get("pharmacyIds") == null
                || !body.get("pharmacyIds").isArray()) {
            return new PharmacyChoice(List.of(), "Choose pharmacies");
        }
        List<UUID> ids = new ArrayList<>();
        for (JsonNode node : body.get("pharmacyIds")) {
            if (node == null || !node.isTextual()) {
                return new PharmacyChoice(List.of(), "Pharmacy is not valid");
            }
            try {
                UUID id = UUID.fromString(node.asText().trim());
                if (!ids.contains(id)) {
                    ids.add(id);
                }
            } catch (IllegalArgumentException exception) {
                return new PharmacyChoice(List.of(), "Pharmacy is not valid");
            }
        }
        if (ids.size() > 20) {
            return new PharmacyChoice(List.of(), "Choose fewer pharmacies");
        }
        return new PharmacyChoice(ids, null);
    }

    public static Arrangement arrange(List<Line> lines, List<Offer> offers, List<UUID> chosen) {
        Map<String, Line> byProduct = new LinkedHashMap<>();
        for (Line line : lines) {
            byProduct.put(line.productId(), line);
        }
        Map<UUID, Map<String, Offer>> stock = new LinkedHashMap<>();
        Map<UUID, PharmacyRef> pharmacies = new LinkedHashMap<>();
        for (Offer offer : offers) {
            if (!byProduct.containsKey(offer.productId())) {
                continue;
            }
            stock.computeIfAbsent(offer.pharmacyId(), key -> new LinkedHashMap<>())
                    .put(offer.productId(), offer);
            pharmacies.putIfAbsent(
                    offer.pharmacyId(), new PharmacyRef(offer.pharmacyId(), offer.pharmacyName(), offer.distanceKm()));
        }
        Map<String, UUID> recommended = assign(byProduct, new ArrayList<>(pharmacies.values()), stock);
        boolean customerChose = chosen != null && !chosen.isEmpty();
        List<PharmacyRef> pool = new ArrayList<>();
        if (customerChose) {
            for (UUID id : chosen) {
                PharmacyRef pharmacy = pharmacies.get(id);
                if (pharmacy != null) {
                    pool.add(pharmacy);
                }
            }
        } else {
            pool.addAll(pharmacies.values());
        }
        Map<String, UUID> assignment = customerChose ? assign(byProduct, pool, stock) : recommended;
        List<LinePlan> linePlans = new ArrayList<>();
        for (Line line : lines) {
            linePlans.add(linePlan(line, assignment.get(line.productId()), stock));
        }
        LinkedHashSet<UUID> shown = new LinkedHashSet<>(pharmacies.keySet());
        if (customerChose) {
            shown.addAll(chosen);
        }
        List<PharmacyPlan> pharmacyPlans = new ArrayList<>();
        for (UUID id : shown) {
            PharmacyRef pharmacy = pharmacies.get(id);
            List<String> supply = pharmacy == null ? List.of() : supply(id, byProduct, stock);
            boolean eligible = !supply.isEmpty();
            List<String> assigned = assigned(id, assignment);
            BigDecimal merchandise = merchandise(assigned, byProduct, stock.getOrDefault(id, Map.of()));
            pharmacyPlans.add(new PharmacyPlan(
                    id,
                    eligible ? pharmacy.name() : null,
                    eligible ? pharmacy.distanceKm() : null,
                    recommended.containsValue(id),
                    customerChose ? chosen.contains(id) : recommended.containsValue(id),
                    eligible,
                    supply,
                    assigned,
                    merchandise));
        }
        pharmacyPlans.sort(Comparator.comparing(
                        (PharmacyPlan plan) -> plan.distanceKm() == null ? Double.MAX_VALUE : plan.distanceKm())
                .thenComparing(PharmacyPlan::pharmacyId));
        return new Arrangement(customerChose ? CHOSEN : RECOMMENDED, linePlans, pharmacyPlans);
    }

    public static BigDecimal payable(BigDecimal merchandise, BigDecimal discount, BigDecimal delivery) {
        return merchandise.subtract(discount).add(delivery).setScale(2, RoundingMode.HALF_UP);
    }

    private static LinePlan linePlan(Line line, UUID assigned, Map<UUID, Map<String, Offer>> stock) {
        List<Offer> alternatives = new ArrayList<>();
        for (Map<String, Offer> shelf : stock.values()) {
            Offer offer = shelf.get(line.productId());
            if (offer != null && offer.availableQty() >= line.qty()) {
                alternatives.add(offer);
            }
        }
        alternatives.sort(Comparator.comparingDouble(Offer::distanceKm).thenComparing(Offer::pharmacyId));
        Offer chosen = null;
        if (assigned != null) {
            Offer offer = stock.getOrDefault(assigned, Map.of()).get(line.productId());
            if (offer != null && offer.availableQty() >= line.qty()) {
                chosen = offer;
            }
        }
        if (chosen == null) {
            return new LinePlan(line.productId(), line.qty(), null, null, null, null, false, alternatives);
        }
        BigDecimal total =
                chosen.onlinePrice().multiply(BigDecimal.valueOf(line.qty())).setScale(2, RoundingMode.HALF_UP);
        return new LinePlan(
                line.productId(),
                line.qty(),
                chosen.pharmacyId(),
                chosen.onlineMrp(),
                chosen.onlinePrice(),
                total,
                true,
                alternatives);
    }

    private static Map<String, UUID> assign(
            Map<String, Line> lines, List<PharmacyRef> pool, Map<UUID, Map<String, Offer>> stock) {
        LinkedHashSet<String> uncovered = new LinkedHashSet<>(lines.keySet());
        Map<String, UUID> assignment = new LinkedHashMap<>();
        List<UUID> picked = new ArrayList<>();
        while (!uncovered.isEmpty()) {
            PharmacyRef best = null;
            int bestCount = 0;
            for (PharmacyRef pharmacy : pool) {
                if (picked.contains(pharmacy.id())) {
                    continue;
                }
                int count = 0;
                for (String productId : uncovered) {
                    if (fills(
                            stock,
                            pharmacy.id(),
                            productId,
                            lines.get(productId).qty())) {
                        count++;
                    }
                }
                if (count == 0) {
                    continue;
                }
                if (best == null || count > bestCount || (count == bestCount && prefer(pharmacy, best))) {
                    best = pharmacy;
                    bestCount = count;
                }
            }
            if (best == null) {
                break;
            }
            picked.add(best.id());
            for (String productId : List.copyOf(uncovered)) {
                if (fills(stock, best.id(), productId, lines.get(productId).qty())) {
                    assignment.put(productId, best.id());
                    uncovered.remove(productId);
                }
            }
        }
        return assignment;
    }

    private static boolean prefer(PharmacyRef candidate, PharmacyRef current) {
        int distance = Double.compare(candidate.distanceKm(), current.distanceKm());
        if (distance != 0) {
            return distance < 0;
        }
        return candidate.id().compareTo(current.id()) < 0;
    }

    private static boolean fills(Map<UUID, Map<String, Offer>> stock, UUID pharmacyId, String productId, int qty) {
        Offer offer = stock.getOrDefault(pharmacyId, Map.of()).get(productId);
        return offer != null && offer.availableQty() >= qty;
    }

    private static List<String> supply(UUID pharmacyId, Map<String, Line> lines, Map<UUID, Map<String, Offer>> stock) {
        List<String> ids = new ArrayList<>();
        for (Line line : lines.values()) {
            if (fills(stock, pharmacyId, line.productId(), line.qty())) {
                ids.add(line.productId());
            }
        }
        return ids;
    }

    private static List<String> assigned(UUID pharmacyId, Map<String, UUID> assignment) {
        List<String> ids = new ArrayList<>();
        for (Map.Entry<String, UUID> entry : assignment.entrySet()) {
            if (pharmacyId.equals(entry.getValue())) {
                ids.add(entry.getKey());
            }
        }
        return ids;
    }

    private static BigDecimal merchandise(List<String> productIds, Map<String, Line> lines, Map<String, Offer> shelf) {
        BigDecimal total = BigDecimal.ZERO;
        for (String productId : productIds) {
            Offer offer = shelf.get(productId);
            Line line = lines.get(productId);
            if (offer == null || line == null) {
                continue;
            }
            total = total.add(offer.onlinePrice().multiply(BigDecimal.valueOf(line.qty())));
        }
        return total.setScale(2, RoundingMode.HALF_UP);
    }

    private record PharmacyRef(UUID id, String name, double distanceKm) {}
}
