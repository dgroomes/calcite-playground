package dgroomes;

import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.java.AbstractQueryableTable;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.linq4j.*;
import org.apache.calcite.linq4j.function.Function1;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.*;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.calcite.schema.impl.AbstractTableQueryable;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * This is my port of {@link org.apache.calcite.adapter.java.ReflectiveSchema}. I want this port to support statistics
 * and Java record classes.
 * <p>
 * A {@link ReflectiveSchema2} is defined by on object whose public fields themselves represent tables. For example:
 * <pre>
 *     class Graph {
 *         public final Node[] nodes;
 *         public final Edge[] edges;
 *     }
 * </pre>
 */
public class ReflectiveSchema2 extends AbstractSchema {
    private final Map<String, Table> tableMap;

    private ReflectiveSchema2(Map<String, Table> tableMap) {
        super();
        this.tableMap = tableMap;
    }

    @Override
    public String toString() {
        return "ReflectiveSchema2(number_of_tables=" + tableMap.size() + ")";
    }

    @Override
    protected Map<String, Table> getTableMap() {
        return tableMap;
    }

    public static <T> ListAsTableDescriptor<T> listAsTable(String tableName, List<T> rows, Class<T> elementType) {
        return new ListAsTableDescriptor<>(tableName, rows, elementType);
    }

    public record ListAsTableDescriptor<T>(String tableName, List<T> rows, Class<T> elementType) {}

    /**
     * Infer the tables from the fields of a given object, and ultimately create a {@link ReflectiveSchema2} from it.
     */
    public static ReflectiveSchema2 create(List<ListAsTableDescriptor<?>> listAsTableDescriptors) {

        // Infer tables from the fields.
        Map<String, Table> tablesByName = new HashMap<>();
        for (ListAsTableDescriptor<?> listAsTableDescriptor : listAsTableDescriptors) {
            String tableName = listAsTableDescriptor.tableName;
            Class<?> elementType = listAsTableDescriptor.elementType;

            List<?> listRepresentationOfTable = listAsTableDescriptor.rows;
            requireNonNull(listRepresentationOfTable, () -> "List-as-table '%s' has null rows".formatted(tableName));
            var enumerable = (Enumerable<?>) Linq4j.asEnumerable(listRepresentationOfTable);
            //noinspection rawtypes,unchecked
            Table table = new TableOverEnumerable(elementType, enumerable);
            tablesByName.put(tableName, table);
        }

        return new ReflectiveSchema2(tablesByName);
    }

    /**
     * Table backed by an enumerable.
     */
    private static class TableOverEnumerable<T> extends AbstractQueryableTable implements Table, ScannableTable {
        private final Enumerable<T> enumerable;
        private final Class<T> elementTypeClass;
        private final Statistic statistic;

        TableOverEnumerable(Class<T> elementType, Enumerable<T> enumerable) {
            this(elementType, enumerable, Statistics.UNKNOWN);
        }

        TableOverEnumerable(Class<T> elementType, Enumerable<T> enumerable,
                            Statistic statistic) {
            super(elementType);
            this.elementTypeClass = elementType;
            this.enumerable = enumerable;
            this.statistic = statistic;
        }

        @Override
        public Statistic getStatistic() {
            return statistic;
        }

        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return ((JavaTypeFactory) typeFactory).createType(elementType);
        }

        @Override
        public Enumerable<@Nullable Object[]> scan(DataContext root) {
            @SuppressWarnings("unchecked") var enumerableCast = (Enumerable<Object>) enumerable;
            return enumerableCast.select(new FieldSelector(elementTypeClass));
        }

        @Override
        public <X> Queryable<X> asQueryable(QueryProvider queryProvider,
                                            SchemaPlus schema, String tableName) {
            return new AbstractTableQueryable<>(queryProvider, schema, this,
                    tableName) {
                @SuppressWarnings("unchecked")
                @Override
                public Enumerator<X> enumerator() {
                    return (Enumerator<X>) enumerable.enumerator();
                }
            };
        }
    }

    /** Function that returns an array of a given object's field values. */
    private static class FieldSelector implements Function1<Object, @Nullable Object[]> {
        private final Field[] fields;

        FieldSelector(Class<?> elementType) {
            this.fields = elementType.getFields();
        }

        @Override
        public @Nullable Object[] apply(Object o) {
            try {
                @Nullable Object[] objects = new Object[fields.length];
                for (int i = 0; i < fields.length; i++) {
                    objects[i] = fields[i].get(o);
                }
                return objects;
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
