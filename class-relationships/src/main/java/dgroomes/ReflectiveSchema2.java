package dgroomes;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.java.AbstractQueryableTable;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.linq4j.*;
import org.apache.calcite.linq4j.function.Function1;
import org.apache.calcite.rel.RelReferentialConstraint;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.*;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.calcite.schema.impl.AbstractTableQueryable;
import org.apache.calcite.util.Util;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
    private final Object tableHolder;
    private final Map<String, Table> tableMap;

    private ReflectiveSchema2(Object tableHolder, Map<String, Table> tableMap) {
        super();
        this.tableHolder = tableHolder;
        this.tableMap = tableMap;
    }

    @Override
    public String toString() {
        return "ReflectiveSchema2(target=" + tableHolder.getClass() + ")";
    }

    @Override
    protected Map<String, Table> getTableMap() {
        return tableMap;
    }

    /**
     * Infer the tables from the fields of a given object, and ultimately create a {@link ReflectiveSchema2} from it.
     *
     * @param tableHolder An instance of a clas where all the fields represent tables.
     */
    public static ReflectiveSchema2 inferSchema(Object tableHolder) {
        Class<?> clazz = tableHolder.getClass();

        // Infer tables from the fields.
        Map<String, Table> tablesByName = new HashMap<>();
        for (Field field : clazz.getFields()) {
            String fieldName = field.getName();
            Class<?> elementType;
            Class<?> fieldType = field.getType();
            if (fieldType.isAssignableFrom(List.class)) {
                Type genericFieldType = field.getGenericType();

                // Ensure the generic field type is indeed a ParameterizedType
                // (e.g., List<String> as opposed to a raw List)
                if (!(genericFieldType instanceof ParameterizedType parameterizedType)) {
                    throw new IllegalArgumentException("Expected a generic type parameter (e.g. List<Foo>) but found a raw List for field " + field);
                }

                Type[] fieldArgTypes = parameterizedType.getActualTypeArguments();
                elementType = (Class<?>) fieldArgTypes[0];
            } else {
                throw new IllegalArgumentException("Only list types are supported for inferring tables from fields.");
            }

            List<?> listRepresentationOfTable;
            try {
                listRepresentationOfTable = (List<?>) field.get(tableHolder);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(
                        "Error while accessing field " + field, e);
            }
            requireNonNull(listRepresentationOfTable, () -> "field " + field + " is null for " + tableHolder);
            var enumerable = (Enumerable<?>) Linq4j.asEnumerable(listRepresentationOfTable);
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

    /**
     * Table based on a Java field.
     *
     * @param <T> element type
     */
    private static class FieldTable<T> extends AbstractQueryableTable implements Table, ScannableTable {
        private final Field field;
        private final Enumerable<T> enumerable;
        private final Class<T> elementTypeClass;
        private Statistic statistic;

        FieldTable(Field field, Class<T> elementType, Enumerable<T> enumerable) {
            this(field, elementType, enumerable, Statistics.UNKNOWN);
        }

        FieldTable(Field field, Class<T> elementType, Enumerable<T> enumerable,
                   Statistic statistic) {
            super(elementType);
            this.elementTypeClass = elementType;
            this.enumerable = enumerable;
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
