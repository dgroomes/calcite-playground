package dgroomes;

import org.apache.calcite.jdbc.CalciteConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Please see the README for more information.
 */
public class Runner {

    private static final Logger log = LoggerFactory.getLogger(Runner.class);
    private CalciteConnection connection;

    public static void main(String[] args) throws SQLException {
        new Runner().run();
    }

    void run() throws SQLException {
        log.info("Let's learn about Apache Calcite! Let's treat local CSV files as tables.");
        log.info("");

        try (var connection = DriverManager.getConnection("jdbc:calcite:");
             var calciteConnection = connection.unwrap(CalciteConnection.class).unwrap(CalciteConnection.class)) {

            this.connection = calciteConnection;

            setupSchema();
            selectAllZips();
            populationByCity();
        }
    }

    /**
     * Do the necessary boilerplate to create the schema and register it with the Calcite machinery. In an application
     * with moderate sophistication, you can avoid this boilerplate by using model files (JSON or YAML) and specifying
     * a {@link org.apache.calcite.schema.SchemaFactory} via the JDBC connection URL.
     */
    private void setupSchema() throws SQLException {
        // Create a schema and register it with the connection
        var salesCsvSchema = CsvSchema.create(new File("geographies-data"));
        connection.getRootSchema().add("GEOGRAPHIES", salesCsvSchema);
        connection.setSchema("GEOGRAPHIES");
    }

    private void selectAllZips() throws SQLException {
        log.info("Select all ZIP codes and their populations...");
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("select * from zips");
        while (resultSet.next()) {
            var zipCode = resultSet.getInt("zip_code");
            var population = resultSet.getInt("population");
            log.info("ZIP code: {}, population: {}", zipCode, formatInteger(population));
        }
        resultSet.close();
        statement.close();

        log.info("");
    }

    private void populationByCity() throws SQLException {
        log.info("Sum up the population of each city...");
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery("""
                select c.name,
                       c.state_code,
                       sum(z.population) as population
                from cities as c
                         join zips z on c.oid = z.city_oid
                group by c.name, c.state_code
                order by population desc""");

        while (resultSet.next()) {
            var population = resultSet.getInt("population");
            var name = resultSet.getString("name");
            var stateCode = resultSet.getString("state_code");
            log.info("Population of {} ({}): {}", name, stateCode, formatInteger(population));
        }
        resultSet.close();
        statement.close();
        log.info("");
    }

    /**
     * Formats an integer value with commas.
     * <p>
     * For example, 1234567 becomes "1,234,567".
     */
    public static String formatInteger(int value) {
        return NumberFormat.getNumberInstance(Locale.US).format(value);
    }
}
