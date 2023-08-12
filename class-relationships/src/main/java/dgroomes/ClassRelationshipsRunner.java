package dgroomes;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
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

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Please see the README for more context.
 */
public class ClassRelationshipsRunner {

    private FrameworkConfig frameworkConfig;
    private SchemaPlus classRelationshipsSchema;


    private static final Logger log = LoggerFactory.getLogger(ClassRelationshipsRunner.class);

    public static void main(String[] args) {
        log.info("Let's reflectively analyze Java class-to-class relationships and query the data with Apache Calcite!");
        new ClassRelationshipsRunner().run();
    }

    public void run() {
        ClassRelationships classRelationships = buildDataSet();
        log.info("Built the final in-memory data set: {}", classRelationships);

        var rootSchema = Frameworks.createRootSchema(true);

        var reflectiveSchema = new ReflectiveSchema(classRelationships);
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

        queryClasses();
        queryFields();
    }

    private static ClassRelationships buildDataSet() {
        ClassGraph classGraph = new ClassGraph().enableSystemJarsAndModules().enableFieldInfo();

        ClassInfoList classInfos;
        try (var scanResult = classGraph.scan()) {
            classInfos = scanResult.getAllClasses();
        }

        List<ClassInfo> classes = new ArrayList<>();
        List<FieldInfo> fields = new ArrayList<>();

        for (var classInfo_ : classInfos) {
            var classInfo = new ClassInfo(classInfo_.getName());
            for (var fieldInfo_ : classInfo_.getFieldInfo()) {
                var fieldInfo = new FieldInfo(fieldInfo_.getName(), classInfo);
                fields.add(fieldInfo);
            }
            classes.add(classInfo);
        }

        return new ClassRelationships(
                classes.toArray(ClassInfo[]::new),
                fields.toArray(FieldInfo[]::new),
                new MethodInfo[]{});
    }

    /**
     * Execute a SQL query over the {@link ClassRelationships} data set.
     *
     * @param sql        The SQL string to execute.
     * @param rowHandler A function to handle each row of the result.
     */
    private void query(String sql, Consumer<Object[]> rowHandler) {
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
            interpreter.forEach(rowHandler);
        }
    }

    /**
     * Simple query over the 'classes' table.
     */
    private void queryClasses() {
        String sql = """
                select c.name
                from classes c
                limit 5
                """;

        query(sql, row -> {
            var name = row[0];
            log.info("Class name '{}'", name);
        });
    }

    /**
     * Simple query over the 'fields' table.
     */
    private void queryFields() {
        String sql = """
                select f.name, f.owningClassName
                from fields f
                limit 5
                """;

        query(sql, row -> {
            var name = row[0];
            var owningClassName = row[1];
            log.info("Field name '{}' on class '{}'", name, owningClassName);
        });
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
