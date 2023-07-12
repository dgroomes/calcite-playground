package dgroomes;

import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.interpreter.Interpreter;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.RelBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * Please see the README for more context.
 */
public class RelationalAlgebraRunner {

    private static final Logger log = LoggerFactory.getLogger(RelationalAlgebraRunner.class);

    /**
     * Note: "oid" means "object ID". It's a unique identifier for that object type.
     */
    public static class City {

        public final int oid;
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
        log.info("Let's learn the Apache Calcite relational algebra API by summing up a sample of ZIP code population data by their city.");

        SchemaPlus geographiesSchema;
        {
            // Let's create the schema objects and wire in some data. For convenience, let's use a POJO-based data set
            // that represents ZIP codes and cities ("geographies").
            var rootSchema = Frameworks.createRootSchema(true);
            var reflectiveSchema = new ReflectiveSchema(new Geographies());
            geographiesSchema = rootSchema.add("geographies", reflectiveSchema);
        }


        FrameworkConfig config = Frameworks.newConfigBuilder().defaultSchema(geographiesSchema).build();

        // Let's build a relational algebra expression directly. While the official Calcite codebase generally tests
        // with SQL, there are some examples that use the relational algebra API, like this: https://github.com/apache/calcite/blob/3c5345c988e43622e7dd1e8197972c7664514da1/core/src/test/java/org/apache/calcite/examples/RelBuilderExample.java#L31
        RelBuilder builder = RelBuilder.create(config);
        RelNode node = builder
                .scan("zips")
                .scan("cities")
                .join(JoinRelType.INNER,
                        builder.equals(
                                builder.field(2, 0, "cityOid"),
                                builder.field(2, 1, "oid")))
                // How can we show the city name instead of the city OID? I want to still group by the OID because
                // that's the right identifier but I want to present the city name. I had an earlier tactic of writing
                // SQL (which I know how to write) and then converting it to a relational algebra expression (which I'm
                // still learning). I should do that.
                .aggregate(builder.groupKey("cityOid"),
                        builder.sum(false, "city_population", builder.field("population")))
                .project(builder.field(0), builder.field("city_population"))
                .build();

        GeographiesDataContext dataContext = new GeographiesDataContext(geographiesSchema, node);

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
     * This part of the Calcite API is a bit odd and circuitous (I think because Calcite is not often used without
     * JDBC). We need to create an instance of a {@link DataContext} and interestingly I think our only option is to
     * implement the interface directly (and eat the cost of unused boilerplate methods).
     * <p>
     * This is adapted from a <a href="https://github.com/apache/calcite/blob/3c5345c988e43622e7dd1e8197972c7664514da1/core/src/test/java/org/apache/calcite/test/InterpreterTest.java#L82">Calcite test suite</a>.
     * I'm so glad I found this otherwise I was about to give up (I had kind of given up before finding this).
     */
    public static class GeographiesDataContext implements DataContext {
        private final SchemaPlus rootSchema;
        private final JavaTypeFactory typeFactory;

        GeographiesDataContext(SchemaPlus rootSchema, RelNode rel) {
            this.rootSchema = rootSchema;
            this.typeFactory = (JavaTypeFactory) rel.getCluster().getTypeFactory();
        }

        @Override
        public SchemaPlus getRootSchema() {
            return rootSchema;
        }

        @Override
        public JavaTypeFactory getTypeFactory() {
            return typeFactory;
        }

        @Override
        public QueryProvider getQueryProvider() {
            return null;
        }

        @Override
        public Object get(String name) {
            return null;
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
