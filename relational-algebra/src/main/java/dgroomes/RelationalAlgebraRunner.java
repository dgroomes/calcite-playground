package dgroomes;

import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.interpreter.Interpreter;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.tools.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Please see the README for more context.
 */
public class RelationalAlgebraRunner {

    private static final Logger log = LoggerFactory.getLogger(RelationalAlgebraRunner.class);

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
        log.info("Let's learn the Apache Calcite relational algebra API!");
        new RelationalAlgebraRunner().run();
    }

    public void run() {
        {
            // Let's create the schema objects and wire in some data. For convenience, let's use a POJO-based data set
            // that represents ZIP codes and cities ("geographies").
            var rootSchema = Frameworks.createRootSchema(true);
            var reflectiveSchema = new ReflectiveSchema(new Geographies());
            geographiesSchema = rootSchema.add("geographies", reflectiveSchema);
        }

        frameworkConfig = Frameworks.newConfigBuilder().defaultSchema(geographiesSchema).build();

        cityPop();
    }

    /**
     * Sum up ZIP code population data to their city.
     */
    private void cityPop() {
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
                // How can we show the city name instead of the city OID? I want to still group by the OID because
                // that's the right identifier, but I want to present the city name. I had an earlier tactic of writing
                // SQL (which I know how to write) and then converting it to a relational algebra expression (which I'm
                // still learning). I should do that.
                .aggregate(builder.groupKey("cityOid"),
                        builder.sum(false, "city_population", builder.field("population")))
                .project(builder.field(0), builder.field("city_population"))
                .build();

        DriverlessDataContext dataContext = new DriverlessDataContext(geographiesSchema, node);

        try (Interpreter interpreter = new Interpreter(dataContext, node)) {
            interpreter.forEach(row -> {
                var city = row[0];
                //noinspection DataFlowIssue
                var population = formatInteger((int) row[1]);
                log.info("City '{}' has a population of {}", city, population);
            });
        }
    }

    /**
     * This is a bit off-topic but I've found it difficult to write working relational expressions. By contrast, I can
     * figure out how to write working SQL queries. Conveniently, you can convert a SQL query to a relational text
     * expression and then I have a better chance at writing the expression in Java.
     */
    private void examineSqlAsRelationalExpression() {
        // SQL Query
        String sql = "SELECT e.name, d.name FROM emps e inner join depts d on e.deptno = d.deptno";

        // Creating planner with default settings (what is a planner?)
        Planner planner = Frameworks.getPlanner(frameworkConfig);

        RelNode node;
        try {
            SqlNode parsedSql = planner.parse(sql);
            SqlNode validatedSql = planner.validate(parsedSql);
            node = planner.rel(validatedSql).rel;
            log.info(RelOptUtil.toString(node));
        } catch (SqlParseException | ValidationException | RelConversionException e) {
            throw new RuntimeException(e);
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
