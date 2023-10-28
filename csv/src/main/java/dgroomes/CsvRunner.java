package dgroomes;

import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.fun.SqlStdOperatorTable;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.tools.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.sql.*;
import java.text.NumberFormat;
import java.util.Locale;

/**
 * Please see the README for more information.
 */
public class CsvRunner {

    private static final Logger log = LoggerFactory.getLogger(CsvRunner.class);
    private Connection connection;
    private CalciteConnection calciteConnection;

    public static void main(String[] args) throws SQLException {
        new CsvRunner().run();
    }

    void run() throws SQLException {
        log.info("Let's learn about Apache Calcite! Let's treat local CSV files as tables.");
        log.info("");

        try (var connection = DriverManager.getConnection("jdbc:calcite:");
             var calciteConnection = connection.unwrap(CalciteConnection.class)) {

            this.connection = connection;
            this.calciteConnection = calciteConnection;
            setupSchema();
            selectAllZips();
            populationByCity();
            zipsHighPopulation();
        }
    }

    /**
     * Do the necessary boilerplate to create the schema and register it with the Calcite machinery. In an application
     * with moderate sophistication, you can avoid this boilerplate by using model files (JSON or YAML) and specifying
     * a {@link org.apache.calcite.schema.SchemaFactory} via the JDBC connection URL.
     */
    private void setupSchema() throws SQLException {
        // Create a schema and register it with the connection
        CsvSchema geographiesSchema = CsvSchema.create(new File("geographies-data"));
        calciteConnection.getRootSchema().add("GEOGRAPHIES", geographiesSchema);
        calciteConnection.setSchema("GEOGRAPHIES");
    }

    private void selectAllZips() throws SQLException {
        log.info("Select all ZIP codes and their populations...");
        Statement statement = calciteConnection.createStatement();
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
        Statement statement = calciteConnection.createStatement();
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

    /**
     * This is a bit off-topic, but I've found it difficult to write working relational expressions. By contrast, I can
     * figure out how to write working SQL queries. Conveniently, you can convert a SQL query to a relational text
     * expression, and then I have a better chance at writing the expression in Java.
     */
    private void examineSqlAsRelationalExpression() {
        // SQL Query
        String sql = """
                select z.zip_code, z.population
                from geographies.zips z
                where z.population > 30000
                """;

        // Creating planner with default settings (what is a planner?)
        Planner planner;
        {
            var schema = calciteConnection.getRootSchema();
            var frameworkConfig = Frameworks.newConfigBuilder().defaultSchema(schema)
                    .defaultSchema(schema)
                    .build();
            planner = Frameworks.getPlanner(frameworkConfig);
        }

        // Convert the SQL query to a relational expression
        try {
            SqlNode parsedSql = planner.parse(sql);
            SqlNode validatedSql = planner.validate(parsedSql);
            RelNode node = planner.rel(validatedSql).rel;
            log.info("\n{}", RelOptUtil.toString(node));
        } catch (SqlParseException | ValidationException | RelConversionException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Instead of writing a SQL query, we can write a relational expression. While SQL queries are excellent for humans
     * to write, relational expressions are better for machines to write. It's good to understand that Calcite's {@link CalciteConnection}
     * lets us do either. It's especially useful to have a working example because the API is indirect and you might
     * never discover it. Specifically, we have to call {@link CalciteConnection#unwrap(Class)} and pass it {@link RelRunner}
     * to get a handle on a special object that can run relational expressions.
     */
    private void zipsHighPopulation() {
        log.info("Find high population ZIPs...");

        // Let's build a relational algebra expression directly. While the official Calcite codebase generally tests
        // with SQL, there are some examples that use the relational algebra API, like this: https://github.com/apache/calcite/blob/3c5345c988e43622e7dd1e8197972c7664514da1/core/src/test/java/org/apache/calcite/examples/RelBuilderExample.java#L31
        RelNode node;
        {
            var schema = calciteConnection.getRootSchema();
            var frameworkConfig = Frameworks.newConfigBuilder().defaultSchema(schema)
                    .defaultSchema(schema)
                    .build();
            RelBuilder builder = RelBuilder.create(frameworkConfig);
            node = builder
                    .scan("GEOGRAPHIES", "ZIPS")
                    .filter(builder.call(SqlStdOperatorTable.GREATER_THAN,
                            builder.field("POPULATION"),
                            builder.literal(30_000)))
                    .project(
                            builder.field("ZIP_CODE"),
                            builder.field("POPULATION"))
                    .build();
        }

        try {
            RelRunner relRunner;
            {
                // This is the tricky bit that was really hard to discover. This RelRunner API is the thing that lets us
                // run relational expressions. But, actually getting a handle on it is tricky. We have to call "unwrap"
                // on the JDBC "Connection" object. But to be fair, the "unwrap" JavaDoc says the method is used "to allow
                // access to non-standard methods" and so this is an idiomatic way to do it.
                relRunner = connection.unwrap(RelRunner.class);
            }

            PreparedStatement preparedStatement = relRunner.prepareStatement(node);
            preparedStatement.execute();
            ResultSet resultSet = preparedStatement.getResultSet();
            while (resultSet.next()) {
                var zipCode = resultSet.getInt("zip_code");
                var population = resultSet.getInt("population");
                log.info("ZIP code: {}, population: {}", zipCode, formatInteger(population));
            }
            resultSet.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
