package dgroomes;

import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.linq4j.QueryProvider;
import org.apache.calcite.rel.RelNode;
import org.apache.calcite.schema.SchemaPlus;

/**
 * A {@link DataContext} that is not backed by a JDBC driver.
 * <p>
 * This part of the Calcite API is a bit odd and circuitous (I think because Calcite is not often used without
 * JDBC). We need to create an instance of a {@link DataContext} and interestingly I think our only option is to
 * implement the interface directly (and eat the cost of unused boilerplate methods).
 * <p>
 * This is adapted from a <a href="https://github.com/apache/calcite/blob/3c5345c988e43622e7dd1e8197972c7664514da1/core/src/test/java/org/apache/calcite/test/InterpreterTest.java#L82">Calcite test suite</a>.
 * I'm so glad I found this otherwise I was about to give up (I had kind of given up before finding this).
 */
public class DriverlessDataContext implements DataContext {
    private final SchemaPlus rootSchema;
    private final JavaTypeFactory typeFactory;

    DriverlessDataContext(SchemaPlus rootSchema, RelNode rel) {
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
