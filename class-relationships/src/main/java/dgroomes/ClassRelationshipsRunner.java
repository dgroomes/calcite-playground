package dgroomes;

import org.apache.calcite.adapter.java.ReflectiveSchema;
import org.apache.calcite.avatica.util.Casing;
import org.apache.calcite.interpreter.Interpreter;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.schema.SchemaPlus;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.sql.parser.SqlParser;
import org.apache.calcite.tools.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Please see the README for more context.
 */
public class ClassRelationshipsRunner {

    private FrameworkConfig frameworkConfig;
    private SchemaPlus classRelationshipsSchema;

    public static class Type {
        public final String name;
        public List<Method> methods = new ArrayList<>();
        public List<Field> fields = new ArrayList<>();

        public Type(String name) {
            this.name = name;
        }
    }

    public static class Method {
        public final String name;
        public final List<Type> parameterTypes = new ArrayList<>();
        public final Type returnType;

        public Method(String name, Type returnType) {
            this.name = name;
            this.returnType = returnType;
        }
    }

    public static class Field {
        public final String name;
        public final Type type;

        public Field(String name, Type type) {
            this.name = name;
            this.type = type;
        }
    }

    public static class ClassRelationships {

        public final Type[] types;

        public ClassRelationships(Type[] types) {
            this.types = types;
        }
    }


    private static final Logger log = LoggerFactory.getLogger(ClassRelationshipsRunner.class);

    public static void main(String[] args) {
        log.info("Let's reflectively analyze Java class-to-class relationships and query the data with Apache Calcite!");
        log.error("NOT YET IMPLEMENTED");
        new ClassRelationshipsRunner().run();
    }

    public void run() {
        var rootSchema = Frameworks.createRootSchema(true);

        var sampleTypes = new Type[]{
                new Type("java.lang.String"),
                new Type("java.lang.Integer"),
                new Type("java.lang.Boolean"),
        };

        var reflectiveSchema = new ReflectiveSchema(new ClassRelationships(sampleTypes));
        classRelationshipsSchema = rootSchema.add("geographies", reflectiveSchema);

        frameworkConfig = Frameworks.newConfigBuilder().defaultSchema(classRelationshipsSchema)
                // The default behavior of the framework config is to uppercase the SQL.
                // This is generally a useful normalization but the reflective schema does
                // not uppercase its table names (e.g. the 'cities' array list is represented
                // as a 'cities' table. So there is a mismatch at the SQL validation time.
                // To work around this, we can use the "unquoted casing unchanged" option.
                .parserConfig(SqlParser.Config.DEFAULT.withUnquotedCasing(Casing.UNCHANGED))
                .defaultSchema(classRelationshipsSchema)
                .build();

        queryTypes();
    }


    /**
     * Simple query over the 'types' table.
     */
    private void queryTypes() {
        String sql = """
                select t.name
                from types t
                limit 2
                """;

        Planner planner = Frameworks.getPlanner(frameworkConfig);

        RelNode node;
        try {
            SqlNode parsedSql = planner.parse(sql);
            SqlNode validatedSql = planner.validate(parsedSql);
            node = planner.rel(validatedSql).rel;
            log.debug("\n{}", RelOptUtil.toString(node));
        } catch (SqlParseException | ValidationException | RelConversionException e) {
            throw new RuntimeException(e);
        }

        DriverlessDataContext dataContext = new DriverlessDataContext(classRelationshipsSchema, node);
        try (Interpreter interpreter = new Interpreter(dataContext, node)) {
            interpreter.forEach(row -> {
                var name = row[0];
                log.info("Type name '{}'", name);
            });
        }
    }
}
