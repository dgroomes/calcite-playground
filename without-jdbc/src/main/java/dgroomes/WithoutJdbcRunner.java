package dgroomes;

import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.avatica.util.Casing;
import org.apache.calcite.interpreter.Interpreter;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.tools.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Please see the README for more context.
 */
public class WithoutJdbcRunner {

    private static final Logger log = LoggerFactory.getLogger(WithoutJdbcRunner.class);

    private FrameworkConfig frameworkConfig;
    private SchemaPlus geographiesSchema;

    public static class City {

        public final int oid; // "oid" means "object ID". It's a unique identifier for the object.
        public final String name;
        public final String stateCode;

        public City(int oid, String name, String stateCode) {
            this.oid = oid;
            this.name = name;
            this.stateCode = stateCode;
        }
    }

    public static class Zip {
        public final int zipCode;
        public final int population;
        public final int cityOid;

        public Zip(int zipCode, int population, int cityOid) {
            this.zipCode = zipCode;
            this.population = population;
            this.cityOid = cityOid;
        }

    }

    public static class Geographies {

        public final City[] cities = {
                new City(1, "Boulder", "CO"),
                new City(2, "Savannah", "GA")
        };

        public final Zip[] zips = new Zip[]{
                new Zip(80301, 18174, 1),
                new Zip(80302, 29384, 1),
                new Zip(80303, 39860, 1),
                new Zip(80304, 21550, 1),
                new Zip(31401, 37544, 2),
                new Zip(31405, 28739, 2),
                new Zip(31406, 34024, 2),
                new Zip(31409, 3509, 2),
                new Zip(31410, 15808, 2),
                new Zip(31411, 4707, 2),
        };
    }

    public static void main(String[] args) {
        log.info("Let's engage core Apache Calcite APIs like the relational algebra API!");
        new WithoutJdbcRunner().run();
    }

    public void run() {
        {
            // Let's create the schema objects and wire in some data. For convenience, let's use a POJO-based data set
            // that represents ZIP codes and cities ("geographies").
            var rootSchema = Frameworks.createRootSchema(true);
            var reflectiveSchema = new ReflectiveSchema(new Geographies());
            geographiesSchema = rootSchema.add("geographies", reflectiveSchema);
        }

        frameworkConfig = Frameworks.newConfigBuilder().defaultSchema(geographiesSchema)
                // The default behavior of the framework config is to uppercase the SQL.
                // This is generally a useful normalization but the reflective schema does
                // not uppercase its table names (e.g. the 'cities' array list is represented
                // as a 'cities' table. So there is a mismatch at the SQL validation time.
                // To work around this, we can use the "unquoted casing unchanged" option.
                .parserConfig(SqlParser.Config.DEFAULT.withUnquotedCasing(Casing.UNCHANGED))
                .defaultSchema(geographiesSchema)
                .build();

        cityPop_relationalExpression();
        cityPop_sql();
    }

    private void cityPop_relationalExpression() {
        log.info("Calculate total city populations by summing up ZIP codes (relational expression)...");

        // Let's build a relational algebra expression directly. While the official Calcite codebase generally tests
        // with SQL, there are some examples that use the relational algebra API, like this: https://github.com/apache/calcite/blob/3c5345c988e43622e7dd1e8197972c7664514da1/core/src/test/java/org/apache/calcite/examples/RelBuilderExample.java#L31
        RelBuilder builder = RelBuilder.create(frameworkConfig);
        RelNode node = builder
                .scan("zips")
                .scan("cities")
                .join(JoinRelType.INNER,
                        builder.equals(
                                builder.field(2, 0, "cityOid"),
                                builder.field(2, 1, "oid")))
                .aggregate(builder.groupKey("cityOid", "name"),
                        builder.sum(false, "city_population", builder.field("population")))
                .project(
                        builder.field("name"),
                        builder.field("cityOid"),
                        builder.field("city_population"))
                .build();

        var dataContext = new DriverlessDataContext(geographiesSchema, node);

        try (Interpreter interpreter = new Interpreter(dataContext, node)) {
            interpreter.forEach(row -> {
                var cityName = row[0];
                var cityOid = row[1];
                //noinspection DataFlowIssue
                var population = formatInteger((int) row[2]);
                log.info("City '{}' ({}) has a population of {}", cityName, cityOid, population);
            });
        }
    }

    /**
     * It's difficult to hand-write relational algebra expressions. By contrast, it's really easy to write SQL because
     * it's a language many know and love. This method converts a SQL query to a relational algebra expression object
     * using core Calcite APIs. This is an absolute boon in jumpstarting the process of writing relational algebra
     * expressions.
     */
    private RelNode convertSqlToRelationalExpression(String sql) {
        log.debug("Converting the following SQL query to a relational expression:\n{}", sql);

        // Creating planner with default settings (what is a planner?)
        Planner planner = Frameworks.getPlanner(frameworkConfig);

        RelNode node;
        try {
            SqlNode parsedSql = planner.parse(sql);
            SqlNode validatedSql = planner.validate(parsedSql);
            node = planner.rel(validatedSql).rel;

            // Turn on debug logging to see the relational algebra expression in text form.
            log.debug("Relational algebra expression (converted from SQL):\n{}", RelOptUtil.toString(node));
        } catch (SqlParseException | ValidationException | RelConversionException e) {
            throw new RuntimeException(e);
        }

        return node;
    }

    private void cityPop_sql() {
        log.info("Calculate total city populations by summing up ZIP codes (SQL)...");

        var sql = """
                select c.name, c.oid, sum(z.population)
                from cities c inner join zips z on c.oid = z.cityOid
                group by c.name, c.oid""";

        RelNode node = convertSqlToRelationalExpression(sql);

        var dataContext = new DriverlessDataContext(geographiesSchema, node);

        try (Interpreter interpreter = new Interpreter(dataContext, node)) {
            interpreter.forEach(row -> {
                var cityName = row[0];
                var cityOid = row[1];
                //noinspection DataFlowIssue
                var population = formatInteger((int) row[2]);
                log.info("City '{}' ({}) has a population of {}", cityName, cityOid, population);
            });
        }
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
