package dgroomes;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfoList;
import org.apache.calcite.jdbc.CalciteConnection;
import org.apache.calcite.plan.RelOptUtil;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.schema.Schema;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.calcite.sql.SqlNode;
import org.apache.calcite.tools.FrameworkConfig;
import org.apache.calcite.tools.Frameworks;
import org.apache.calcite.tools.Planner;
import org.apache.calcite.tools.RelRunner;
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

            FrameworkConfig frameworkConfig = Frameworks.newConfigBuilder()
                    .defaultSchema(calciteConnection.getRootSchema())
                    .build();
            planner = Frameworks.getPlanner(frameworkConfig);

            relRunner = connection.unwrap(RelRunner.class);

            queryClassesWithMostFields();
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
     * Execute a SQL query over the "class relationships" data set.
     *
     * @param sql        The SQL string to execute.
     * @param rowHandler A function to handle each row of the result.
     */
    private void query(String sql, RowHandler rowHandler) throws Exception {
        RelNode node;
        {
            SqlNode parsedSql = planner.parse(sql);
            SqlNode validatedSql = planner.validate(parsedSql);
            node = planner.rel(validatedSql).rel;
            log.debug("\n{}", RelOptUtil.toString(node));
        }

        var now = Instant.now();
        PreparedStatement preparedStatement = relRunner.prepareStatement(node);
        preparedStatement.execute();
        ResultSet resultSet = preparedStatement.getResultSet();
        while (resultSet.next()) {
            rowHandler.handle(resultSet);
        }
        resultSet.close();
        var end = Instant.now();
        var duration = Duration.between(now, end);
        log.info("Query executed in {}.", duration);
    }

    private void queryClassesWithMostFields() throws Exception {
        String sql = """
                select c.name, count(*)
                from class_relationships.classes c
                join class_relationships.fields f on c.name = f.owningClassName
                group by c.name
                order by count(*) desc
                limit 10
                """;

        query(sql, resultSet -> {
            var name = resultSet.getString(1);
            var count = resultSet.getLong(2);
            log.info("Class name '{}' has {} fields", name, Util.formatInteger(count));
        });
    }
}

interface RowHandler {
    void handle(ResultSet resultSet) throws SQLException;
}
