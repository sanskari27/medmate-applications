package in.medmate.pharmacy_server.customer.store;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
@Profile({"local", "prod", "identity-it"})
public class AppSectionStore {

    private static final String SECTION_COLUMNS =
            "id, title, image_key, image_content_type, banner_key, banner_content_type, position, created_at, updated_at";
    private static final String CATEGORY_COLUMNS = "id, section_id, category, position, created_at, updated_at";
    private static final String IMAGE_COLUMNS = "id, category_id, image_key, image_content_type, position, created_at";

    private final JdbcClient jdbc;

    public AppSectionStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record SectionRow(
            UUID id,
            String title,
            String imageKey,
            String imageContentType,
            String bannerKey,
            String bannerContentType,
            int position,
            Instant createdAt,
            Instant updatedAt) {}

    public record CategoryRow(
            UUID id, UUID sectionId, String category, int position, Instant createdAt, Instant updatedAt) {}

    public record ImageRow(
            UUID id, UUID categoryId, String imageKey, String imageContentType, int position, Instant createdAt) {}

    public record ImageWrite(UUID id, String imageKey, String imageContentType, int position) {}

    public record CategoryWrite(UUID id, String category, int position, List<ImageWrite> images) {}

    public List<SectionRow> list() {
        return jdbc.sql("SELECT " + SECTION_COLUMNS + " FROM app_section ORDER BY position, lower(title), id")
                .query(AppSectionStore::section)
                .list();
    }

    public List<CategoryRow> listCategories() {
        return jdbc.sql("SELECT " + CATEGORY_COLUMNS
                        + " FROM app_section_category ORDER BY section_id, position, lower(category), id")
                .query(AppSectionStore::category)
                .list();
    }

    public List<ImageRow> listImages() {
        return jdbc.sql("SELECT " + IMAGE_COLUMNS
                        + " FROM app_section_category_image ORDER BY category_id, position, id")
                .query(AppSectionStore::image)
                .list();
    }

    public Optional<SectionRow> find(UUID id) {
        return jdbc.sql("SELECT " + SECTION_COLUMNS + " FROM app_section WHERE id = :id")
                .param("id", id)
                .query(AppSectionStore::section)
                .optional();
    }

    public List<CategoryRow> categories(UUID sectionId) {
        return jdbc.sql("SELECT " + CATEGORY_COLUMNS
                        + " FROM app_section_category WHERE section_id = :sectionId"
                        + " ORDER BY position, lower(category), id")
                .param("sectionId", sectionId)
                .query(AppSectionStore::category)
                .list();
    }

    public List<ImageRow> images(List<UUID> categoryIds) {
        if (categoryIds.isEmpty()) {
            return List.of();
        }
        return jdbc.sql("SELECT " + IMAGE_COLUMNS
                        + " FROM app_section_category_image WHERE category_id IN (:ids) ORDER BY position, id")
                .param("ids", categoryIds)
                .query(AppSectionStore::image)
                .list();
    }

    public SectionRow insert(
            UUID id,
            String title,
            String imageKey,
            String imageContentType,
            String bannerKey,
            String bannerContentType,
            int position,
            Instant now) {
        jdbc.sql(
                        """
                        INSERT INTO app_section (
                            id, title, image_key, image_content_type, banner_key, banner_content_type,
                            position, created_at, updated_at
                        ) VALUES (
                            :id, :title, :imageKey, :imageContentType, :bannerKey, :bannerContentType,
                            :position, :now, :now
                        )
                        """)
                .param("id", id)
                .param("title", title, Types.VARCHAR)
                .param("imageKey", imageKey, Types.VARCHAR)
                .param("imageContentType", imageContentType, Types.VARCHAR)
                .param("bannerKey", bannerKey, Types.VARCHAR)
                .param("bannerContentType", bannerContentType, Types.VARCHAR)
                .param("position", position)
                .param("now", Timestamp.from(now))
                .update();
        return new SectionRow(id, title, imageKey, imageContentType, bannerKey, bannerContentType, position, now, now);
    }

    public Optional<SectionRow> update(
            UUID id,
            String title,
            String imageKey,
            String imageContentType,
            String bannerKey,
            String bannerContentType,
            int position,
            Instant now) {
        int updated = jdbc.sql(
                        """
                        UPDATE app_section
                        SET title = :title,
                            image_key = :imageKey,
                            image_content_type = :imageContentType,
                            banner_key = :bannerKey,
                            banner_content_type = :bannerContentType,
                            position = :position,
                            updated_at = :now
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("title", title, Types.VARCHAR)
                .param("imageKey", imageKey, Types.VARCHAR)
                .param("imageContentType", imageContentType, Types.VARCHAR)
                .param("bannerKey", bannerKey, Types.VARCHAR)
                .param("bannerContentType", bannerContentType, Types.VARCHAR)
                .param("position", position)
                .param("now", Timestamp.from(now))
                .update();
        if (updated == 0) {
            return Optional.empty();
        }
        return find(id);
    }

    public void replaceCategories(UUID sectionId, List<CategoryWrite> categories, Instant now) {
        jdbc.sql("DELETE FROM app_section_category WHERE section_id = :sectionId")
                .param("sectionId", sectionId)
                .update();
        Timestamp stamp = Timestamp.from(now);
        for (CategoryWrite category : categories) {
            jdbc.sql(
                            """
                            INSERT INTO app_section_category (
                                id, section_id, category, position, created_at, updated_at
                            ) VALUES (
                                :id, :sectionId, :category, :position, :now, :now
                            )
                            """)
                    .param("id", category.id())
                    .param("sectionId", sectionId)
                    .param("category", category.category(), Types.VARCHAR)
                    .param("position", category.position())
                    .param("now", stamp)
                    .update();
            for (ImageWrite image : category.images()) {
                jdbc.sql(
                                """
                                INSERT INTO app_section_category_image (
                                    id, category_id, image_key, image_content_type, position, created_at
                                ) VALUES (
                                    :id, :categoryId, :imageKey, :imageContentType, :position, :now
                                )
                                """)
                        .param("id", image.id())
                        .param("categoryId", category.id())
                        .param("imageKey", image.imageKey(), Types.VARCHAR)
                        .param("imageContentType", image.imageContentType(), Types.VARCHAR)
                        .param("position", image.position())
                        .param("now", stamp)
                        .update();
            }
        }
    }

    public boolean delete(UUID id) {
        return jdbc.sql("DELETE FROM app_section WHERE id = :id")
                        .param("id", id)
                        .update()
                > 0;
    }

    private static SectionRow section(ResultSet result, int number) throws SQLException {
        return new SectionRow(
                result.getObject("id", UUID.class),
                result.getString("title"),
                result.getString("image_key"),
                result.getString("image_content_type"),
                result.getString("banner_key"),
                result.getString("banner_content_type"),
                result.getInt("position"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant());
    }

    private static CategoryRow category(ResultSet result, int number) throws SQLException {
        return new CategoryRow(
                result.getObject("id", UUID.class),
                result.getObject("section_id", UUID.class),
                result.getString("category"),
                result.getInt("position"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant());
    }

    private static ImageRow image(ResultSet result, int number) throws SQLException {
        return new ImageRow(
                result.getObject("id", UUID.class),
                result.getObject("category_id", UUID.class),
                result.getString("image_key"),
                result.getString("image_content_type"),
                result.getInt("position"),
                result.getTimestamp("created_at").toInstant());
    }
}
