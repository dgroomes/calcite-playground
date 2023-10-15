package dgroomes;

import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.java.AbstractQueryableTable;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.linq4j.*;
import org.apache.calcite.linq4j.function.Function1;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.*;
import org.apache.calcite.schema.impl.AbstractTableQueryable;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.Field;
import java.util.List;

import static java.util.Objects.requireNonNull;

/**
 * Table backed by an enumerable.
 */
public class TableOverEnumerable<T> extends AbstractQueryableTable implements Table, ScannableTable {
    private final Enumerable<T> enumerable;
    private final Enumerable<@Nullable Object[]> enumerableObjects;
    private final Statistic statistic;

    private TableOverEnumerable(Class<T> elementType, Enumerable<T> enumerable,
                                Enumerable<@Nullable Object[]> enumerableObjects, Statistic statistic) {
        super(elementType);
        this.enumerable = enumerable;
        this.enumerableObjects = enumerableObjects;
        this.statistic = statistic;
    }

    public static <T> Table listAsTable(List<T> rows, Class<T> elementType) {
        requireNonNull(rows);

        // This is odd. Turning a "List<T>" into an "Enumerable<T>" and then into an "Enumerable<Object>". It's too
        // much. But this is what Calcite does in the ReflectiveSchema and related machinery.
        Enumerable<T> enumerable = Linq4j.asEnumerable(rows);
        FieldSelector fieldSelector = new FieldSelector(elementType);
        @SuppressWarnings("unchecked") var enumerableCast = (Enumerable<Object>) enumerable;
        Enumerable<Object[]> enumerableObjects = enumerableCast.select(fieldSelector);
        return new TableOverEnumerable<>(elementType, enumerable, enumerableObjects, Statistics.UNKNOWN);
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
        return enumerableObjects;
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
