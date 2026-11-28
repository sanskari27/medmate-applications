package in.medmate.pharmacy_server.customer.web;

import com.fasterxml.jackson.databind.JsonNode;
import in.medmate.pharmacy_server.catalog.CatalogAccess;
import in.medmate.pharmacy_server.catalog.CatalogStore;
import in.medmate.pharmacy_server.customer.AppSectionRules;
import in.medmate.pharmacy_server.customer.CustomerAccess;
import in.medmate.pharmacy_server.customer.store.AppSectionStore;
import in.medmate.pharmacy_server.identity.access.AccountPrincipal;
import in.medmate.pharmacy_server.identity.config.MedmateIdentityProperties;
import in.medmate.pharmacy_server.identity.documents.DocumentStore;
import in.medmate.pharmacy_server.shared.error.ApiProblem;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile({"local", "prod", "identity-it"})
@ConditionalOnProperty(prefix = "medmate.catalog", name = "url")
public class AppSectionService {

    private final AppSectionStore sections;
    private final CatalogStore catalog;
    private final DocumentStore documents;
    private final MedmateIdentityProperties properties;
    private final Clock clock;

    public AppSectionService(
            AppSectionStore sections,
            CatalogStore catalog,
            DocumentStore documents,
            MedmateIdentityProperties properties,
            Clock clock) {
        this.sections = sections;
        this.catalog = catalog;
        this.documents = documents;
        this.properties = properties;
        this.clock = clock;
    }

    public AppSectionViews.ListResponse listForStaff(AccountPrincipal actor) {
        requireStaff(actor);
        return listed();
    }

    public AppSectionViews.ListResponse listForCustomer(AccountPrincipal actor) {
        requireCustomer(actor);
        return listed();
    }

    @Transactional
    public AppSectionViews.One create(
            AccountPrincipal actor,
            JsonNode body,
            AppSectionRules.Upload image,
            AppSectionRules.Upload banner,
            List<AppSectionRules.Upload> categoryImages) {
        requireStaff(actor);
        return save(null, body, image, banner, categoryImages, true);
    }

    @Transactional
    public AppSectionViews.One update(
            AccountPrincipal actor,
            UUID id,
            JsonNode body,
            AppSectionRules.Upload image,
            AppSectionRules.Upload banner,
            List<AppSectionRules.Upload> categoryImages) {
        requireStaff(actor);
        AppSectionStore.SectionRow current = sections.find(id).orElseThrow(AppSectionService::missing);
        return save(current, body, image, banner, categoryImages, false);
    }

    @Transactional
    public void delete(AccountPrincipal actor, UUID id) {
        requireStaff(actor);
        AppSectionStore.SectionRow current = sections.find(id).orElseThrow(AppSectionService::missing);
        Set<String> keys = keys(current);
        if (!sections.delete(id)) {
            throw missing();
        }
        discard(keys);
    }

    private AppSectionViews.One save(
            AppSectionStore.SectionRow current,
            JsonNode body,
            AppSectionRules.Upload image,
            AppSectionRules.Upload banner,
            List<AppSectionRules.Upload> categoryImages,
            boolean creating) {
        AppSectionRules.Prepared prepared = AppSectionRules.prepare(body);
        if (prepared.problem() != null) {
            throw problem(HttpStatus.BAD_REQUEST, prepared.problem());
        }
        String imageProblem = AppSectionRules.imageProblem(image, creating);
        if (imageProblem != null) {
            throw problem(HttpStatus.BAD_REQUEST, imageProblem);
        }
        String bannerProblem = AppSectionRules.fileProblem(banner, creating, "Banner");
        if (bannerProblem != null) {
            throw problem(HttpStatus.BAD_REQUEST, bannerProblem);
        }
        List<AppSectionRules.Upload> files = categoryImages == null ? List.of() : categoryImages;
        if (files.size() != prepared.draft().newImages()) {
            throw problem(HttpStatus.BAD_REQUEST, "Category images do not match");
        }
        for (AppSectionRules.Upload file : files) {
            String fileProblem = AppSectionRules.fileProblem(file, true, "Category image");
            if (fileProblem != null) {
                throw problem(HttpStatus.BAD_REQUEST, fileProblem);
            }
        }
        List<String> names = canonical(prepared.draft());
        Map<UUID, AppSectionStore.ImageRow> owned = owned(current);
        checkKept(prepared.draft(), owned);
        UUID sectionId = current == null ? UUID.randomUUID() : current.id();
        List<String> uploaded = new ArrayList<>();
        try {
            Stored imageStored = store(sectionId, "image", image, uploaded);
            Stored bannerStored = store(sectionId, "banner", banner, uploaded);
            String imageKey = imageStored == null ? current.imageKey() : imageStored.key();
            String imageType = imageStored == null ? current.imageContentType() : imageStored.contentType();
            String bannerKey = bannerStored == null ? current.bannerKey() : bannerStored.key();
            String bannerType = bannerStored == null ? current.bannerContentType() : bannerStored.contentType();
            List<AppSectionStore.CategoryWrite> categories =
                    categories(sectionId, names, prepared.draft(), owned, files, uploaded);
            Set<String> previous = current == null ? Set.of() : keys(current);
            AppSectionStore.SectionRow row = write(
                    current,
                    sectionId,
                    prepared.draft().title(),
                    imageKey,
                    imageType,
                    bannerKey,
                    bannerType,
                    prepared.draft().position(),
                    categories);
            Set<String> kept = keys(row, categories);
            Set<String> removed = new LinkedHashSet<>(previous);
            removed.removeAll(kept);
            discard(removed);
            return one(row, categories);
        } catch (RuntimeException exception) {
            discard(uploaded);
            if (exception instanceof DuplicateKeyException duplicate) {
                throw problem(HttpStatus.BAD_REQUEST, duplicateMessage(duplicate));
            }
            throw exception;
        }
    }

    private AppSectionStore.SectionRow write(
            AppSectionStore.SectionRow current,
            UUID sectionId,
            String title,
            String imageKey,
            String imageType,
            String bannerKey,
            String bannerType,
            int position,
            List<AppSectionStore.CategoryWrite> categories) {
        Instant now = clock.instant();
        AppSectionStore.SectionRow row = current == null
                ? sections.insert(sectionId, title, imageKey, imageType, bannerKey, bannerType, position, now)
                : sections.update(sectionId, title, imageKey, imageType, bannerKey, bannerType, position, now)
                        .orElseThrow(AppSectionService::missing);
        sections.replaceCategories(sectionId, categories, now);
        return row;
    }

    private List<AppSectionStore.CategoryWrite> categories(
            UUID sectionId,
            List<String> names,
            AppSectionRules.Draft draft,
            Map<UUID, AppSectionStore.ImageRow> owned,
            List<AppSectionRules.Upload> files,
            List<String> uploaded) {
        List<AppSectionStore.CategoryWrite> categories = new ArrayList<>();
        int fileIndex = 0;
        for (int index = 0; index < draft.categories().size(); index++) {
            AppSectionRules.CategoryDraft category = draft.categories().get(index);
            List<AppSectionStore.ImageWrite> images = new ArrayList<>();
            int position = 0;
            for (AppSectionRules.ImageSlot slot : category.images()) {
                if (slot.id() == null) {
                    AppSectionRules.Upload file = files.get(fileIndex++);
                    UUID imageId = UUID.randomUUID();
                    Stored stored = store(sectionId, "categories/" + imageId, file, uploaded);
                    images.add(new AppSectionStore.ImageWrite(imageId, stored.key(), stored.contentType(), position++));
                } else {
                    AppSectionStore.ImageRow existing = owned.get(slot.id());
                    images.add(new AppSectionStore.ImageWrite(
                            existing.id(), existing.imageKey(), existing.imageContentType(), position++));
                }
            }
            categories.add(
                    new AppSectionStore.CategoryWrite(UUID.randomUUID(), names.get(index), index, List.copyOf(images)));
        }
        return categories;
    }

    private Stored store(UUID sectionId, String name, AppSectionRules.Upload upload, List<String> uploaded) {
        if (upload == null || upload.body() == null || upload.body().length == 0) {
            return null;
        }
        String contentType = AppSectionRules.media(upload.contentType());
        String key = properties.uploadPrefix() + "/app-sections/" + sectionId + "/" + name + "-" + UUID.randomUUID();
        documents.put(key, contentType, upload.body());
        uploaded.add(key);
        return new Stored(key, contentType);
    }

    private List<String> canonical(AppSectionRules.Draft draft) {
        List<String> names = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (AppSectionRules.CategoryDraft category : draft.categories()) {
            String name = catalog.canonicalCategory(category.category())
                    .orElseThrow(() -> problem(HttpStatus.BAD_REQUEST, "Category was not found"));
            if (!seen.add(name.toLowerCase(Locale.ROOT))) {
                throw problem(HttpStatus.BAD_REQUEST, "Category is already listed");
            }
            names.add(name);
        }
        return names;
    }

    private Map<UUID, AppSectionStore.ImageRow> owned(AppSectionStore.SectionRow current) {
        Map<UUID, AppSectionStore.ImageRow> owned = new HashMap<>();
        if (current == null) {
            return owned;
        }
        List<UUID> categoryIds = sections.categories(current.id()).stream()
                .map(AppSectionStore.CategoryRow::id)
                .toList();
        if (categoryIds.isEmpty()) {
            return owned;
        }
        for (AppSectionStore.ImageRow image : sections.images(categoryIds)) {
            owned.put(image.id(), image);
        }
        return owned;
    }

    private static void checkKept(AppSectionRules.Draft draft, Map<UUID, AppSectionStore.ImageRow> owned) {
        Set<UUID> used = new HashSet<>();
        for (AppSectionRules.CategoryDraft category : draft.categories()) {
            for (AppSectionRules.ImageSlot slot : category.images()) {
                if (slot.id() != null && (!owned.containsKey(slot.id()) || !used.add(slot.id()))) {
                    throw problem(HttpStatus.BAD_REQUEST, "Category image is not valid");
                }
            }
        }
    }

    private Set<String> keys(AppSectionStore.SectionRow row) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(row.imageKey());
        keys.add(row.bannerKey());
        List<UUID> categoryIds = sections.categories(row.id()).stream()
                .map(AppSectionStore.CategoryRow::id)
                .toList();
        if (!categoryIds.isEmpty()) {
            for (AppSectionStore.ImageRow image : sections.images(categoryIds)) {
                keys.add(image.imageKey());
            }
        }
        keys.remove(null);
        return keys;
    }

    private static Set<String> keys(AppSectionStore.SectionRow row, List<AppSectionStore.CategoryWrite> categories) {
        Set<String> keys = new LinkedHashSet<>();
        keys.add(row.imageKey());
        keys.add(row.bannerKey());
        for (AppSectionStore.CategoryWrite category : categories) {
            for (AppSectionStore.ImageWrite image : category.images()) {
                keys.add(image.imageKey());
            }
        }
        keys.remove(null);
        return keys;
    }

    private void discard(Iterable<String> keys) {
        for (String key : keys) {
            if (key != null && !key.isBlank()) {
                documents.delete(key);
            }
        }
    }

    private AppSectionViews.ListResponse listed() {
        Map<UUID, List<AppSectionStore.CategoryRow>> categories = new HashMap<>();
        for (AppSectionStore.CategoryRow category : sections.listCategories()) {
            categories
                    .computeIfAbsent(category.sectionId(), id -> new ArrayList<>())
                    .add(category);
        }
        Map<UUID, List<AppSectionStore.ImageRow>> images = new HashMap<>();
        for (AppSectionStore.ImageRow image : sections.listImages()) {
            images.computeIfAbsent(image.categoryId(), id -> new ArrayList<>()).add(image);
        }
        return new AppSectionViews.ListResponse(sections.list().stream()
                .map(row -> section(row, categories.getOrDefault(row.id(), List.of()), images))
                .toList());
    }

    private AppSectionViews.One one(AppSectionStore.SectionRow row, List<AppSectionStore.CategoryWrite> categories) {
        return new AppSectionViews.One(new AppSectionViews.Section(
                row.id(),
                row.title(),
                row.position(),
                media(row.imageContentType(), row.imageKey()),
                media(row.bannerContentType(), row.bannerKey()),
                categories.stream().map(this::category).toList()));
    }

    private AppSectionViews.Section section(
            AppSectionStore.SectionRow row,
            List<AppSectionStore.CategoryRow> categories,
            Map<UUID, List<AppSectionStore.ImageRow>> images) {
        return new AppSectionViews.Section(
                row.id(),
                row.title(),
                row.position(),
                media(row.imageContentType(), row.imageKey()),
                media(row.bannerContentType(), row.bannerKey()),
                categories.stream()
                        .map(category -> new AppSectionViews.Category(
                                category.id(),
                                category.category(),
                                category.position(),
                                images.getOrDefault(category.id(), List.of()).stream()
                                        .map(image -> new AppSectionViews.CategoryImage(
                                                image.id(), image.imageContentType(), documents.url(image.imageKey())))
                                        .toList()))
                        .toList());
    }

    private AppSectionViews.Category category(AppSectionStore.CategoryWrite category) {
        return new AppSectionViews.Category(
                category.id(),
                category.category(),
                category.position(),
                category.images().stream()
                        .map(image -> new AppSectionViews.CategoryImage(
                                image.id(), image.imageContentType(), documents.url(image.imageKey())))
                        .toList());
    }

    private AppSectionViews.Media media(String contentType, String key) {
        return new AppSectionViews.Media(contentType, documents.url(key));
    }

    private static String duplicateMessage(DuplicateKeyException exception) {
        String message = String.valueOf(exception.getMessage());
        Throwable cause = exception.getMostSpecificCause();
        if (cause != null && cause.getMessage() != null) {
            message = message + " " + cause.getMessage();
        }
        if (message.contains("app_section_category")) {
            return "Category is already listed";
        }
        return "Name is already listed";
    }

    private static void requireStaff(AccountPrincipal actor) {
        if (!CatalogAccess.write(actor)) {
            throw problem(HttpStatus.FORBIDDEN, "This action is not allowed.");
        }
    }

    private static void requireCustomer(AccountPrincipal actor) {
        if (!CustomerAccess.allowed(actor)) {
            throw problem(HttpStatus.FORBIDDEN, "This action is not allowed.");
        }
    }

    private static ApiProblem missing() {
        return problem(HttpStatus.NOT_FOUND, "Section was not found");
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

    private record Stored(String key, String contentType) {}
}
