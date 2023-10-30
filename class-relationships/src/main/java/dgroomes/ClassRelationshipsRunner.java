package dgroomes;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import org.apache.calcite.adapter.enumerable.EnumerableConvention;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.rel.core.JoinRelType;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.sql.fun.SqlLibraryOperators;
import org.apache.calcite.sql.parser.SqlParseException;
import org.apache.calcite.tools.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static dgroomes.TableOverEnumerable.listAsTable;

/**
 * Please see the README for more context.
 */
public class ClassRelationshipsRunner {

    private final int takeFirstNClasses;
    public final List<ClassInfo> classes = new ArrayList<>();
    public final List<FieldInfo> fields = new ArrayList<>();
    public final List<MethodInfo> methods = new ArrayList<>();

    private static final Logger log = LoggerFactory.getLogger(ClassRelationshipsRunner.class);
    private RelRunner relRunner;
    private Planner planner;
    private FrameworkConfig frameworkConfig;

    public ClassRelationshipsRunner(int takeFirstNClasses) {
        this.takeFirstNClasses = takeFirstNClasses;
    }

    public static void main(String[] args) throws Exception {
        log.info("Let's reflectively analyze Java class-to-class relationships and query the data with Apache Calcite!");

        int takeFirstNClasses;
        String takeFirstNClassesEnv = System.getenv("TAKE_FIRST_N_CLASSES");

        if (takeFirstNClassesEnv != null) {
            try {
                takeFirstNClasses = Integer.parseInt(takeFirstNClassesEnv);
            } catch (NumberFormatException e) {
                var msg = "The value in the environment variable 'TAKE_FIRST_N_CLASSES' ('%s') is not a number.".formatted(takeFirstNClassesEnv);
                throw new IllegalArgumentException(msg);
            }
        } else {
            takeFirstNClasses = Integer.MAX_VALUE;
        }

        var runner = new ClassRelationshipsRunner(takeFirstNClasses);
        runner.run();
    }

    public void run() throws Exception {

        try (var connection = DriverManager.getConnection("jdbc:calcite:")) {

            Schema schema = buildDataSetAndSchema();
            var calciteConnection = connection.unwrap(CalciteConnection.class);
            calciteConnection.getRootSchema().add("CLASS_RELATIONSHIPS", schema);
            calciteConnection.setSchema("CLASS_RELATIONSHIPS");

            frameworkConfig = Frameworks.newConfigBuilder()
                    .defaultSchema(calciteConnection.getRootSchema())
                    .build();
            planner = Frameworks.getPlanner(frameworkConfig);

            relRunner = connection.unwrap(RelRunner.class);

            //            examineSqlAsRelationalExpression();
            queryFieldsLike("%x%");
        }
    }

    private Schema buildDataSetAndSchema() {
        ClassGraph classGraph = new ClassGraph().enableSystemJarsAndModules().enableFieldInfo();

        ClassInfoList classInfos;
        try (var scanResult = classGraph.scan()) {
            classInfos = scanResult.getAllClasses();
        }

        for (int i = 0; i < classInfos.size() && i < takeFirstNClasses; i++) {
            var classInfo_ = classInfos.get(i);
            var classInfo = new ClassInfo(classInfo_.getName());
            for (var fieldInfo_ : classInfo_.getFieldInfo()) {
                var fieldInfo = new FieldInfo(fieldInfo_.getName(), classInfo);
                fields.add(fieldInfo);
            }
            classes.add(classInfo);
        }

        log.info("Built the final in-memory data set. {} class, {} fields, {} methods", Util.formatInteger(classes.size()), Util.formatInteger(fields.size()), Util.formatInteger(methods.size()));

        Map<String, Table> tablesByName = Map.of(
                "CLASSES", listAsTable(classes, ClassInfo.class),
                "FIELDS", listAsTable(fields, FieldInfo.class),
                "METHODS", listAsTable(methods, MethodInfo.class));
        return new AbstractSchema() {
            @Override
            protected Map<String, Table> getTableMap() {
                return tablesByName;
            }
        };
    }

    /**
     * Execute a relational expression over the "class relationships" data set.
     *
     * @param relNode
     * @param rowHandler A function to handle each row of the result.
     */
    private void query(RelNode relNode, RowHandler rowHandler) throws Exception {
        var now = Instant.now();
        PreparedStatement preparedStatement = relRunner.prepareStatement(relNode);
        preparedStatement.execute();
        ResultSet resultSet = preparedStatement.getResultSet();
        int rowCount = 0;
        while (resultSet.next()) {
            rowCount++;
            rowHandler.handle(resultSet);
        }
        resultSet.close();
        var end = Instant.now();
        var duration = Duration.between(now, end);
        log.info("Query executed in {}. Fetched {} rows.", duration, rowCount);
    }

    /**
     * Find fields whose name matches the given pattern. This joins the "CLASSES" and "FIELDS" tables.
     *
     * @param pattern the pattern to match. For example, "%x%" will match all fields whose name contains the letter 'x'.
     */
    private void queryFieldsLike(String pattern) throws Exception {
        RelBuilder builder = RelBuilder.create(frameworkConfig);
        RelNode relNode = builder
                .adoptConvention(EnumerableConvention.INSTANCE) // This is not necessary, but we know this expression is going to use the enumerable calling convention in the end.
                .scan("CLASS_RELATIONSHIPS", "CLASSES")
                .scan("CLASS_RELATIONSHIPS", "FIELDS")
                .join(JoinRelType.INNER,
                        builder.equals(
                                builder.field(2, 0, "NAME"),
                                builder.field(2, 1, "OWNINGCLASSNAME")))
                .project(
                        builder.field(1, "CLASSES", "NAME"),
                        builder.field(1, "FIELDS", "NAME"))
                .filter(builder.call(SqlLibraryOperators.ILIKE, builder.field(1), builder.literal(pattern)))
                .sortLimit(0, 10, builder.field(1, "CLASSES", "NAME"))
                .build();

        log.debug("Relational algebra expression:\n{}", RelOptUtil.toString(relNode));

        //        relNode = examineSqlAsRelationalExpression();

        query(relNode, resultSet -> {
            var className = resultSet.getString(1);
            var fieldName = resultSet.getString(2);
            log.info("Class/field '{}/{}'", className, fieldName);
        });
    }

    /**
     * It's difficult to hand-write relational algebra expressions. By contrast, it's really easy to write SQL because
     * it's a language many know and love. This method converts a SQL query to a relational algebra expression object
     * using core Calcite APIs. This is an absolute boon in jumpstarting the process of writing relational algebra
     * expressions.
     */
    private RelNode examineSqlAsRelationalExpression() {
        String sql = """
                select c.name, f.name
                from class_relationships.classes c
                left join class_relationships.fields f on c.name = f.owningClassName
                where f.name like '%x%'
                limit 10
                """;

        log.info("Converting the following SQL query to a relational expression:\n{}", sql);

        RelNode node;
        try {
            SqlNode parsedSql = planner.parse(sql);
            SqlNode validatedSql = planner.validate(parsedSql);
            node = planner.rel(validatedSql).rel;
            log.info("Relational algebra expression (converted from SQL):\n{}", RelOptUtil.toString(node));
            return node;
        } catch (SqlParseException | ValidationException | RelConversionException e) {
            throw new RuntimeException(e);
        }
    }
}

interface RowHandler {
    void handle(ResultSet resultSet) throws SQLException;
}
