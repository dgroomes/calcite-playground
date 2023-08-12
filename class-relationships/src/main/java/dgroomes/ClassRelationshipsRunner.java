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

import java.util.ArrayList;
import java.util.List;

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
        public final List<MethodInfo> methods = new ArrayList<>();
        public final List<FieldInfo> field = new ArrayList<>();

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
        public ClassInfo declaredClass; // TODO lazily set

        public FieldInfo(String name, ClassInfo owningClass) {
            this.name = name;
            this.owningClass = owningClass;
        }
    }

    public static class ClassRelationships {
        public final ClassInfo[] classes;
        public final FieldInfo[] fields;
        public final MethodInfo[] methods;

        public ClassRelationships(ClassInfo[] classes, FieldInfo[] fields, MethodInfo[] methods) {
            this.classes = classes;
            this.fields = fields;
            this.methods = methods;
        }
    }


    private static final Logger log = LoggerFactory.getLogger(ClassRelationshipsRunner.class);

    public static void main(String[] args) {
        log.info("Let's reflectively analyze Java class-to-class relationships and query the data with Apache Calcite!");
        new ClassRelationshipsRunner().run();
    }

    public void run() {
        var rootSchema = Frameworks.createRootSchema(true);

        ClassInfoList classInfos;
        try (var scanResult = new ClassGraph().enableSystemJarsAndModules().scan()) {
            classInfos = scanResult.getAllClasses();
        }

        var classes = classInfos.stream()
                .map(it -> new ClassInfo(it.getName()))
                .toArray(ClassInfo[]::new);

        var reflectiveSchema = new ReflectiveSchema(new ClassRelationships(classes, new FieldInfo[]{}, new MethodInfo[]{}));
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
}
