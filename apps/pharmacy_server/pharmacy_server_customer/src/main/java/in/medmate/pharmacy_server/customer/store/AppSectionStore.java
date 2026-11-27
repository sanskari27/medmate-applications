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

    private static final String COLUMNS =
            "id, category, image_key, image_content_type, position, created_at, updated_at";

    private final JdbcClient jdbc;

    public AppSectionStore(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public record SectionRow(
            UUID id,
            String category,
            String imageKey,
            String imageContentType,
            int position,
            Instant createdAt,
            Instant updatedAt) {}

    public List<SectionRow> list() {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_section ORDER BY position, lower(category), id")
                .query(this::row)
                .list();
    }

    public Optional<SectionRow> find(UUID id) {
        return jdbc.sql("SELECT " + COLUMNS + " FROM app_section WHERE id = :id")
                .param("id", id)
                .query(this::row)
                .optional();
    }

    public SectionRow insert(
            UUID id, String category, String imageKey, String imageContentType, int position, Instant now) {
        jdbc.sql(
                        """
                        INSERT INTO app_section (
                            id, category, image_key, image_content_type, position, created_at, updated_at
                        ) VALUES (
                            :id, :category, :imageKey, :imageContentType, :position, :now, :now
                        )
                        """)
                .param("id", id)
                .param("category", category, Types.VARCHAR)
                .param("imageKey", imageKey, Types.VARCHAR)
                .param("imageContentType", imageContentType, Types.VARCHAR)
                .param("position", position)
                .param("now", Timestamp.from(now))
                .update();
        return new SectionRow(id, category, imageKey, imageContentType, position, now, now);
    }

    public Optional<SectionRow> update(
            UUID id, String category, String imageKey, String imageContentType, int position, Instant now) {
        int updated = jdbc.sql(
                        """
                        UPDATE app_section
                        SET category = :category,
                            image_key = :imageKey,
                            image_content_type = :imageContentType,
                            position = :position,
                            updated_at = :now
                        WHERE id = :id
                        """)
                .param("id", id)
                .param("category", category, Types.VARCHAR)
                .param("imageKey", imageKey, Types.VARCHAR)
                .param("imageContentType", imageContentType, Types.VARCHAR)
                .param("position", position)
                .param("now", Timestamp.from(now))
                .update();
        if (updated == 0) {
            return Optional.empty();
        }
        return find(id);
    }

    public boolean delete(UUID id) {
        return jdbc.sql("DELETE FROM app_section WHERE id = :id")
                        .param("id", id)
                        .update()
                > 0;
    }

    private SectionRow row(ResultSet result, int number) throws SQLException {
        return new SectionRow(
                result.getObject("id", UUID.class),
                result.getString("category"),
                result.getString("image_key"),
                result.getString("image_content_type"),
                result.getInt("position"),
                result.getTimestamp("created_at").toInstant(),
                result.getTimestamp("updated_at").toInstant());
    }
}
