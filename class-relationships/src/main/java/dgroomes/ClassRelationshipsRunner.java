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

/**
 * Please see the README for more context.
 */
public class ClassRelationshipsRunner {

    private FrameworkConfig frameworkConfig;
    private SchemaPlus classRelationshipsSchema;

    /**
     * This is purposely named after the {@link io.github.classgraph.ClassInfo} class in ClassGraph because it is an
     * excellent model, but I need a precise and small sub-scope of {@link io.github.classgraph.ClassInfo} to model the
     * data as a "table" for Apache Calcite.
     */
    public static class ClassInfo {
        public final String name;

        public ClassInfo(String name) {
            this.name = name;
        }
    }

    /**
     * Named after the {@link io.github.classgraph.MethodInfo}.
     */
    public static class MethodInfo {
        public final String name;
        public final List<ClassInfo> parameterClasses = new ArrayList<>();
        public final ClassInfo returnClass;

        public MethodInfo(String name, ClassInfo returnClass) {
            this.name = name;
            this.returnClass = returnClass;
        }
    }

    /**
     * Named after the {@link io.github.classgraph.FieldInfo}.
     */
    public static class FieldInfo {
        public final String name;
        public final ClassInfo owningClass;

        /**
         * It's redundant to store the owning class name, but it's necessary to have a join column.
         */
        public final String owningClassName;
        public ClassInfo declaredClass; // TODO lazily set

        public FieldInfo(String name, ClassInfo owningClass) {
            this.name = name;
            this.owningClass = owningClass;
            owningClassName = owningClass.name;
        }
    }

    /**
     * This is a schema-like dataset. Each field (array of objects) is like a table of rows.
     */
    public static class ClassRelationships {
        public final ClassInfo[] classes;
        public final FieldInfo[] fields;
        public final MethodInfo[] methods;

        public ClassRelationships(ClassInfo[] classes, FieldInfo[] fields, MethodInfo[] methods) {
            this.classes = classes;
            this.fields = fields;
            this.methods = methods;
        }

        @Override
        public String toString() {
            return "ClassRelationships{" +
                    "classes=" + formatInteger(classes.length) +
                    ", fields=" + formatInteger(fields.length) +
                    ", methods=" + formatInteger(methods.length) +
                    '}';
        }
    }


    private static final Logger log = LoggerFactory.getLogger(ClassRelationshipsRunner.class);

    public static void main(String[] args) {
        log.info("Let's reflectively analyze Java class-to-class relationships and query the data with Apache Calcite!");
        new ClassRelationshipsRunner().run();
    }

    public void run() {
        var rootSchema = Frameworks.createRootSchema(true);

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

        ClassRelationships classRelationships = new ClassRelationships(
                classes.toArray(ClassInfo[]::new),
                fields.toArray(FieldInfo[]::new),
                new MethodInfo[]{});

        log.info("Built the final in-memory data set: {}", classRelationships);

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
                log.info("Class name '{}'", name);
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
