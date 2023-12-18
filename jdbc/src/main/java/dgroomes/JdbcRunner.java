package dgroomes;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

/**
 * Please see the README for context.
 */
public class JdbcRunner {

    private static final Logger log = LoggerFactory.getLogger(JdbcRunner.class);

    /**
     * Note: "mem" tells the H2 driver to create an in-memory database. "some-db-name" indicates that the in-memory
     * database should be named "some-db-name". This is a pretty arbitrary thing, but I think you have to provide a name?
     */
    private static final String JDBC_URL = "jdbc:h2:mem:some-db-name";

    public static void main(String... args) throws SQLException {
        var connection = DriverManager.getConnection(JDBC_URL);

        try (var stmt = connection.createStatement()) {

            stmt.executeUpdate(readClasspathResource("/observations-schema.sql"));
            stmt.executeUpdate(readClasspathResource("/observations-data.sql"));

            ResultSet rs = stmt.executeQuery("SELECT o.id, o.observation, ot.description as type FROM observations o inner join observation_types ot on o.type_id = ot.id");

            while (rs.next()) {
                var id = rs.getInt("id");
                var observation = rs.getString("observation");
                var type = rs.getString("type");
                var record = new Observation(id, observation, type);
                log.info("Found this observation: {}", record);
            }
        }
    }

    private static String readClasspathResource(String path) {
        try {
            InputStream stream = Objects.requireNonNull(JdbcRunner.class.getResourceAsStream(path));
            return new String(stream.readAllBytes());
        } catch (IOException e) {
            throw new IllegalStateException("Unexpected exception while reading ");
        }
    }
}
