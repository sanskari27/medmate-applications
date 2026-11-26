package in.medmate.pharmacy_server.customer.web;

import java.util.List;
import java.util.UUID;

public final class AddressViews {

    private AddressViews() {}

    public record AddressView(
            UUID id,
            String label,
            String line1,
            String line2,
            String landmark,
            String city,
            String pincode,
            Double latitude,
            Double longitude,
            String receiverName,
            String receiverPhone,
            boolean callOnArrival,
            boolean leaveWithSecurity,
            boolean dontRingBell,
            boolean liftNotWorking,
            String riderNote,
            boolean selected,
            String createdAt,
            String updatedAt) {}

    public record ListResponse(List<AddressView> addresses) {}

    public record OneResponse(AddressView address) {}
}
