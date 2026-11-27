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
    public AppSectionViews.One create(AccountPrincipal actor, JsonNode body, AppSectionRules.Upload image) {
        requireStaff(actor);
        AppSectionRules.Prepared prepared = AppSectionRules.prepare(body);
        if (prepared.problem() != null) {
            throw problem(HttpStatus.BAD_REQUEST, prepared.problem());
        }
        String imageProblem = AppSectionRules.imageProblem(image, true);
        if (imageProblem != null) {
            throw problem(HttpStatus.BAD_REQUEST, imageProblem);
        }
        String category = category(prepared.draft().category());
        UUID id = UUID.randomUUID();
        String contentType = AppSectionRules.media(image.contentType());
        String key = imageKey(id);
        documents.put(key, contentType, image.body());
        try {
            return one(sections.insert(
                    id, category, key, contentType, prepared.draft().position(), clock.instant()));
        } catch (DuplicateKeyException exception) {
            documents.delete(key);
            throw problem(HttpStatus.BAD_REQUEST, "Category is already listed");
        }
    }

    @Transactional
    public AppSectionViews.One update(AccountPrincipal actor, UUID id, JsonNode body, AppSectionRules.Upload image) {
        requireStaff(actor);
        AppSectionStore.SectionRow current = sections.find(id).orElseThrow(AppSectionService::missing);
        AppSectionRules.Prepared prepared = AppSectionRules.prepare(body);
        if (prepared.problem() != null) {
            throw problem(HttpStatus.BAD_REQUEST, prepared.problem());
        }
        String imageProblem = AppSectionRules.imageProblem(image, false);
        if (imageProblem != null) {
            throw problem(HttpStatus.BAD_REQUEST, imageProblem);
        }
        String category = category(prepared.draft().category());
        String key = current.imageKey();
        String contentType = current.imageContentType();
        if (image != null && image.body() != null && image.body().length > 0) {
            contentType = AppSectionRules.media(image.contentType());
            key = imageKey(id);
            documents.delete(current.imageKey());
            documents.put(key, contentType, image.body());
        }
        try {
            return sections.update(
                            id, category, key, contentType, prepared.draft().position(), clock.instant())
                    .map(this::one)
                    .orElseThrow(AppSectionService::missing);
        } catch (DuplicateKeyException exception) {
            throw problem(HttpStatus.BAD_REQUEST, "Category is already listed");
        }
    }

    @Transactional
    public void delete(AccountPrincipal actor, UUID id) {
        requireStaff(actor);
        AppSectionStore.SectionRow current = sections.find(id).orElseThrow(AppSectionService::missing);
        if (!sections.delete(id)) {
            throw missing();
        }
        documents.delete(current.imageKey());
    }

    private AppSectionViews.ListResponse listed() {
        return new AppSectionViews.ListResponse(
                sections.list().stream().map(this::section).toList());
    }

    private String category(String requested) {
        return catalog.canonicalCategory(requested)
                .orElseThrow(() -> problem(HttpStatus.BAD_REQUEST, "Category was not found"));
    }

    private AppSectionViews.One one(AppSectionStore.SectionRow row) {
        return new AppSectionViews.One(section(row));
    }

    private AppSectionViews.Section section(AppSectionStore.SectionRow row) {
        return new AppSectionViews.Section(
                row.id(),
                row.category(),
                row.position(),
                new AppSectionViews.Image(row.imageContentType(), documents.url(row.imageKey())));
    }

    private String imageKey(UUID id) {
        return properties.uploadPrefix() + "/app-sections/" + id;
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
}
