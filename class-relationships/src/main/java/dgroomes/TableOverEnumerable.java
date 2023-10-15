package dgroomes;

import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.java.AbstractQueryableTable;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.linq4j.*;
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
    private final Enumerable<T> rowAsTEnumerable;
    private final Enumerable<@Nullable Object[]> rowAsArrayEnumerable;
    private final Statistic statistic;

    private TableOverEnumerable(Class<T> elementType, Enumerable<T> rowAsTEnumerable,
                                Enumerable<@Nullable Object[]> rowAsArrayEnumerable, Statistic statistic) {
        super(elementType);
        this.rowAsTEnumerable = rowAsTEnumerable;
        this.rowAsArrayEnumerable = rowAsArrayEnumerable;
        this.statistic = statistic;
    }

    /**
     * Create a Calcite {@link Table} backed by a {@link List} of objects.
     */
    public static <T> Table listAsTable(List<T> rows, Class<T> elementType) {
        requireNonNull(rows);

        // This enumerable of "T" is a nice high level representation of rows of data. But, Calcite also needs a
        // somewhat more primitive enumerable of "Object[]" to be able to do its work. Each "Object[]" is the column
        // values of a row.
        Enumerable<T> rowAsTEnumerable = Linq4j.asEnumerable(rows);
        Enumerable<Object[]> rowAsArrayEnumerable;

        {
            Field[] fields = elementType.getFields();
            rowAsArrayEnumerable = rowAsTEnumerable.select(o -> {
                try {
                    Object[] objects = new Object[fields.length];
                    for (int i = 0; i < fields.length; i++) {
                        objects[i] = fields[i].get(o);
                    }
                    return objects;
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        return new TableOverEnumerable<>(elementType, rowAsTEnumerable, rowAsArrayEnumerable, Statistics.UNKNOWN);
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
        return rowAsArrayEnumerable;
    }

    @Override
    public <X> Queryable<X> asQueryable(QueryProvider queryProvider,
                                        SchemaPlus schema, String tableName) {
        return new AbstractTableQueryable<>(queryProvider, schema, this,
                tableName) {
            @SuppressWarnings("unchecked")
            @Override
            public Enumerator<X> enumerator() {
                return (Enumerator<X>) rowAsTEnumerable.enumerator();
            }
        };
    }
}
