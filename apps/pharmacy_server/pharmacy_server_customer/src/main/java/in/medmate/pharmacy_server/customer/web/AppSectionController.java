package in.medmate.pharmacy_server.customer.web;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.customer.AppSectionRules;
import in.medmate.pharmacy_server.identity.web.Current;
import in.medmate.pharmacy_server.shared.error.ApiProblem;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@Profile({"local", "prod", "identity-it"})
@ConditionalOnProperty(prefix = "medmate.catalog", name = "url")
public class AppSectionController {

    private final AppSectionService sections;

    public AppSectionController(AppSectionService sections) {
        this.sections = sections;
    }

    @GetMapping("/api/v1/app-sections")
    public AppSectionViews.ListResponse list(Authentication authentication) {
        return sections.listForStaff(Current.require(authentication));
    }

    @GetMapping("/api/v1/customer/app-sections")
    public AppSectionViews.ListResponse listForCustomer(Authentication authentication) {
        return sections.listForCustomer(Current.require(authentication));
    }

    @PostMapping(path = "/api/v1/app-sections", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AppSectionViews.One> create(
            Authentication authentication,
            @RequestPart("section") JsonNode section,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "banner", required = false) MultipartFile banner,
            @RequestPart(value = "categoryImage", required = false) List<MultipartFile> categoryImages) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(sections.create(
                        Current.require(authentication),
                        section,
                        read(image, "Image"),
                        read(banner, "Banner"),
                        reads(categoryImages)));
    }

    @PutMapping(path = "/api/v1/app-sections/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AppSectionViews.One update(
            Authentication authentication,
            @PathVariable UUID id,
            @RequestPart("section") JsonNode section,
            @RequestPart(value = "image", required = false) MultipartFile image,
            @RequestPart(value = "banner", required = false) MultipartFile banner,
            @RequestPart(value = "categoryImage", required = false) List<MultipartFile> categoryImages) {
        return sections.update(
                Current.require(authentication),
                id,
                section,
                read(image, "Image"),
                read(banner, "Banner"),
                reads(categoryImages));
    }

    @DeleteMapping("/api/v1/app-sections/{id}")
    public ResponseEntity<Void> delete(Authentication authentication, @PathVariable UUID id) {
        sections.delete(Current.require(authentication), id);
        return ResponseEntity.noContent().build();
    }

    private static List<AppSectionRules.Upload> reads(List<MultipartFile> files) {
        if (files == null) {
            return List.of();
        }
        List<AppSectionRules.Upload> uploads = new ArrayList<>();
        for (MultipartFile file : files) {
            uploads.add(read(file, "Category image"));
        }
        return uploads;
    }

    private static AppSectionRules.Upload read(MultipartFile file, String name) {
        if (file == null || file.isEmpty()) {
            return null;
        }
        try {
            return new AppSectionRules.Upload(file.getContentType(), file.getBytes());
        } catch (IOException exception) {
            throw new ApiProblem(HttpStatus.BAD_REQUEST, "Bad request", name + " must be an image");
        }
    }
}
