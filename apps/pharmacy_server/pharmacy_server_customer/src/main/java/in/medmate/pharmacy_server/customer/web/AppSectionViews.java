package in.medmate.pharmacy_server.customer.web;

import java.util.List;
import java.util.UUID;

public final class AppSectionViews {

    private AppSectionViews() {}

    public record Image(String contentType, String url) {}

    public record Section(UUID id, String category, int position, Image image) {}

    public record ListResponse(List<Section> sections) {}

    public record One(Section section) {}
}
