package dgroomes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.java.AbstractQueryableTable;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.linq4j.*;
import org.apache.calcite.linq4j.function.Function1;
import org.apache.calcite.linq4j.tree.Primitive;
import org.apache.calcite.rel.RelReferentialConstraint;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.*;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.calcite.schema.impl.AbstractTableQueryable;
import org.apache.calcite.util.Util;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.Field;
import java.util.Collections;
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
    private final Object target;
    private final Map<String, Table> tableMap;

    private ReflectiveSchema2(Object tableHolder, Map<String, Table> tableMap) {
        super();
        this.target = tableHolder;
        this.tableMap = tableMap;
    }

    @Override
    public String toString() {
        return "ReflectiveSchema2(target=" + target + ")";
    }

    @Override
    protected Map<String, Table> getTableMap() {
        return tableMap;
    }

    /**
     * @param tableHolder An instance of a clas where all the fields represent tables.
     */
    public static ReflectiveSchema2 create(Object tableHolder) {
        Class<?> clazz = tableHolder.getClass();

        // Infer tables from the fields.
        Map<String, Table> tablesByName = new HashMap<>();
        for (Field field : clazz.getFields()) {
            String fieldName = field.getName();
            Class<?> elementType;
            Class<?> fieldType = field.getType();
            if (fieldType.isArray()) {
                elementType = fieldType.getComponentType();
            } else {
                throw new IllegalArgumentException("Only array types are supported");
            }
            Object o;
            try {
                o = field.get(tableHolder);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(
                        "Error while accessing field " + field, e);
            }
            requireNonNull(o, () -> "field " + field + " is null for " + tableHolder);
            var enumerable = toEnumerable(o);
            //noinspection rawtypes,unchecked
            Table table = new FieldTable(field, elementType, enumerable);
            tablesByName.put(fieldName, table);
        }

        // Unique-Key - Foreign-Key
        for (Field field : clazz.getFields()) {
            if (RelReferentialConstraint.class.isAssignableFrom(field.getType())) {
                RelReferentialConstraint rc;
                try {
                    rc = (RelReferentialConstraint) field.get(tableHolder);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(
                            "Error while accessing field " + field, e);
                }
                requireNonNull(rc, () -> "field must not be null: " + field);
                FieldTable<?> table =
                        (FieldTable<?>) tablesByName.get(Util.last(rc.getSourceQualifiedName()));
                assert table != null;
                List<RelReferentialConstraint> referentialConstraints =
                        table.getStatistic().getReferentialConstraints();
                if (referentialConstraints == null) {
                    // This enables to keep the same Statistics.of below
                    referentialConstraints = ImmutableList.of();
                }
                table.statistic =
                        Statistics.of(
                                ImmutableList.copyOf(
                                        Iterables.concat(referentialConstraints,
                                                Collections.singleton(rc))));
            }
        }

        return new ReflectiveSchema2(tableHolder, tablesByName);
    }

    private static Enumerable<?> toEnumerable(Object o) {
        if (o.getClass().isArray()) {
            if (o instanceof Object[]) {
                return Linq4j.asEnumerable((Object[]) o);
            } else {
                return Linq4j.asEnumerable(Primitive.asList(o));
            }
        }
        if (o instanceof Iterable) {
            return Linq4j.asEnumerable((Iterable<?>) o);
        }
        throw new RuntimeException(
                "Cannot convert " + o.getClass() + " into a Enumerable");
    }

    /** Table that is implemented by reading from a Java object. */
    private static class ReflectiveTable<T>
            extends AbstractQueryableTable
            implements Table, ScannableTable {
        private final Enumerable<T> enumerable;
        private final Class<T> elementTypeClass;

        ReflectiveTable(Class<T> elementType, Enumerable<T> enumerable) {
            super(elementType);
            this.enumerable = enumerable;
            this.elementTypeClass = elementType;
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

    /**
     * Table based on a Java field.
     *
     * @param <T> element type
     */
    private static class FieldTable<T> extends ReflectiveTable<T> {
        private final Field field;
        private Statistic statistic;

        FieldTable(Field field, Class<T> elementType, Enumerable<T> enumerable) {
            this(field, elementType, enumerable, Statistics.UNKNOWN);
        }

        FieldTable(Field field, Class<T> elementType, Enumerable<T> enumerable,
                   Statistic statistic) {
            super(elementType, enumerable);
            this.field = field;
            this.statistic = statistic;
        }

        @Override
        public String toString() {
            return "Relation {field=" + field.getName() + "}";
        }

        @Override
        public Statistic getStatistic() {
            return statistic;
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
