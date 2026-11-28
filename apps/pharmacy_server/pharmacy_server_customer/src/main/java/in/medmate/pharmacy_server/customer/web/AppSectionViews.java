package in.medmate.pharmacy_server.customer.web;

import java.util.List;
import java.util.UUID;

public final class AppSectionViews {

    private AppSectionViews() {}

    public record Media(String contentType, String url) {}

    public record CategoryImage(UUID id, String contentType, String url) {}

    public record Category(UUID id, String category, int position, List<CategoryImage> images) {}

    public record Section(UUID id, String title, int position, Media image, Media banner, List<Category> categories) {}

    public record ListResponse(List<Section> sections) {}

    public record One(Section section) {}
}
