package dgroomes;

import com.google.common.collect.*;
import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.enumerable.EnumUtils;
import org.apache.calcite.adapter.java.AbstractQueryableTable;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.linq4j.*;
import org.apache.calcite.linq4j.function.Function1;
import org.apache.calcite.linq4j.tree.Expression;
import org.apache.calcite.linq4j.tree.Expressions;
import org.apache.calcite.linq4j.tree.Primitive;
import org.apache.calcite.rel.RelReferentialConstraint;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.*;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.calcite.schema.impl.AbstractTableQueryable;
import org.apache.calcite.schema.impl.ReflectiveFunctionBase;
import org.apache.calcite.util.BuiltInMethod;
import org.apache.calcite.util.Util;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.lang.reflect.*;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static java.util.Objects.requireNonNull;

/**
 * This is my port of {@link org.apache.calcite.adapter.java.ReflectiveSchema}. I want this port to support statistics
 * and Java record classes.
 */
public class ReflectiveSchema2
        extends AbstractSchema {
    private final Class clazz;
    private final Object target;
    private @MonotonicNonNull Map<String, Table> tableMap;
    private @MonotonicNonNull Multimap<String, Function> functionMap;

    /**
     * Creates a ReflectiveSchema.
     *
     * @param target Object whose fields will be sub-objects of the schema
     */
    public ReflectiveSchema2(Object target) {
        super();
        this.clazz = target.getClass();
        this.target = target;
    }

    @Override
    public String toString() {
        return "ReflectiveSchema(target=" + target + ")";
    }

    /**
     * Returns the wrapped object.
     *
     * <p>May not appear to be used, but is used in generated code via
     * {@link BuiltInMethod#REFLECTIVE_SCHEMA_GET_TARGET}.
     */
    public Object getTarget() {
        return target;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    protected Map<String, Table> getTableMap() {
        if (tableMap == null) {
            tableMap = createTableMap();
        }
        return tableMap;
    }

    private Map<String, Table> createTableMap() {
        final ImmutableMap.Builder<String, Table> builder = ImmutableMap.builder();
        for (Field field : clazz.getFields()) {
            final String fieldName = field.getName();
            final Table table = fieldRelation(field);
            if (table == null) {
                continue;
            }
            builder.put(fieldName, table);
        }
        Map<String, Table> tableMap = builder.build();
        // Unique-Key - Foreign-Key
        for (Field field : clazz.getFields()) {
            if (RelReferentialConstraint.class.isAssignableFrom(field.getType())) {
                RelReferentialConstraint rc;
                try {
                    rc = (RelReferentialConstraint) field.get(target);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(
                            "Error while accessing field " + field, e);
                }
                requireNonNull(rc, () -> "field must not be null: " + field);
                FieldTable table =
                        (FieldTable) tableMap.get(Util.last(rc.getSourceQualifiedName()));
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
        return tableMap;
    }

    @Override
    protected Multimap<String, Function> getFunctionMultimap() {
        if (functionMap == null) {
            functionMap = createFunctionMap();
        }
        return functionMap;
    }

    private Multimap<String, Function> createFunctionMap() {
        final ImmutableMultimap.Builder<String, Function> builder =
                ImmutableMultimap.builder();
        for (Method method : clazz.getMethods()) {
            final String methodName = method.getName();
            if (method.getDeclaringClass() == Object.class
                    || methodName.equals("toString")) {
                continue;
            }
            if (TranslatableTable.class.isAssignableFrom(method.getReturnType())) {
                final TableMacro tableMacro =
                        new MethodTableMacro(this, method);
                builder.put(methodName, tableMacro);
            }
        }
        return builder.build();
    }

    /**
     * Returns an expression for the object wrapped by this schema (not the
     * schema itself).
     */
    Expression getTargetExpression(@Nullable SchemaPlus parentSchema, String name) {
        return EnumUtils.convert(
                Expressions.call(
                        Schemas.unwrap(
                                getExpression(parentSchema, name),
                                ReflectiveSchema2.class),
                        BuiltInMethod.REFLECTIVE_SCHEMA_GET_TARGET.method),
                target.getClass());
    }

    /**
     * Returns a table based on a particular field of this schema. If the
     * field is not of the right type to be a relation, returns null.
     */
    private <T> @Nullable Table fieldRelation(final Field field) {
        final Type elementType = getElementType(field.getType());
        if (elementType == null) {
            return null;
        }
        Object o;
        try {
            o = field.get(target);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(
                    "Error while accessing field " + field, e);
        }
        requireNonNull(o, () -> "field " + field + " is null for " + target);
        @SuppressWarnings("unchecked") final Enumerable<T> enumerable = toEnumerable(o);
        return new FieldTable<>(field, elementType, enumerable);
    }

    /**
     * Deduces the element type of a collection;
     * same logic as {@link #toEnumerable}.
     */
    private static @Nullable Type getElementType(Class clazz) {
        if (clazz.isArray()) {
            return clazz.getComponentType();
        }
        if (Iterable.class.isAssignableFrom(clazz)) {
            return Object.class;
        }
        return null; // not a collection/array/iterable
    }

    private static Enumerable toEnumerable(final Object o) {
        if (o.getClass().isArray()) {
            if (o instanceof Object[]) {
                return Linq4j.asEnumerable((Object[]) o);
            } else {
                return Linq4j.asEnumerable(Primitive.asList(o));
            }
        }
        if (o instanceof Iterable) {
            return Linq4j.asEnumerable((Iterable) o);
        }
        throw new RuntimeException(
                "Cannot convert " + o.getClass() + " into a Enumerable");
    }

    /** Table that is implemented by reading from a Java object. */
    private static class ReflectiveTable
            extends AbstractQueryableTable
            implements Table, ScannableTable {
        private final Enumerable enumerable;

        ReflectiveTable(Type elementType, Enumerable enumerable) {
            super(elementType);
            this.enumerable = enumerable;
        }

        @Override
        public RelDataType getRowType(RelDataTypeFactory typeFactory) {
            return ((JavaTypeFactory) typeFactory).createType(elementType);
        }

        @Override
        public Statistic getStatistic() {
            return Statistics.UNKNOWN;
        }

        @Override
        public Enumerable<@Nullable Object[]> scan(DataContext root) {
            if (elementType == Object[].class) {
                //noinspection unchecked
                return enumerable;
            } else {
                //noinspection unchecked
                return enumerable.select(new FieldSelector((Class) elementType));
            }
        }

        @Override
        public <T> Queryable<T> asQueryable(QueryProvider queryProvider,
                                            SchemaPlus schema, String tableName) {
            return new AbstractTableQueryable<T>(queryProvider, schema, this,
                    tableName) {
                @SuppressWarnings("unchecked")
                @Override
                public Enumerator<T> enumerator() {
                    return (Enumerator<T>) enumerable.enumerator();
                }
            };
        }
    }

    /**
     * Factory that creates a schema by instantiating an object and looking at
     * its public fields.
     *
     * <p>The following example instantiates a {@code FoodMart} object as a schema
     * that contains tables called {@code EMPS} and {@code DEPTS} based on the
     * object's fields.
     *
     * <blockquote><pre>
     * schemas: [
     *     {
     *       name: "foodmart",
     *       type: "custom",
     *       factory: "org.apache.calcite.adapter.java.ReflectiveSchema$Factory",
     *       operand: {
     *         class: "com.acme.FoodMart",
     *         staticMethod: "instance"
     *       }
     *     }
     *   ]
     * &nbsp;
     * class FoodMart {
     *   public static final FoodMart instance() {
     *     return new FoodMart();
     *   }
     * &nbsp;
     *   Employee[] EMPS;
     *   Department[] DEPTS;
     * }</pre></blockquote>
     */
    public static class Factory implements SchemaFactory {
        @Override
        public Schema create(SchemaPlus parentSchema, String name,
                             Map<String, Object> operand) {
            Class<?> clazz;
            Object target;
            final Object className = operand.get("class");
            if (className != null) {
                try {
                    clazz = Class.forName((String) className);
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException("Error loading class " + className, e);
                }
            } else {
                throw new RuntimeException("Operand 'class' is required");
            }
            final Object methodName = operand.get("staticMethod");
            if (methodName != null) {
                try {
                    //noinspection unchecked
                    Method method = clazz.getMethod((String) methodName);
                    target = method.invoke(null);
                    requireNonNull(target, () -> "method " + method + " returns null");
                } catch (Exception e) {
                    throw new RuntimeException("Error invoking method " + methodName, e);
                }
            } else {
                try {
                    final Constructor<?> constructor = clazz.getConstructor();
                    target = constructor.newInstance();
                } catch (Exception e) {
                    throw new RuntimeException("Error instantiating class " + className,
                            e);
                }
            }
            return new ReflectiveSchema2(target);
        }
    }

    /** Table macro based on a Java method. */
    private static class MethodTableMacro extends ReflectiveFunctionBase
            implements TableMacro {
        private final ReflectiveSchema2 schema;

        MethodTableMacro(ReflectiveSchema2 schema, Method method) {
            super(method);
            this.schema = schema;
            assert TranslatableTable.class.isAssignableFrom(method.getReturnType())
                    : "Method should return TranslatableTable so the macro can be "
                    + "expanded";
        }

        @Override
        public String toString() {
            return "Member {method=" + method + "}";
        }

        @Override
        public TranslatableTable apply(final List<? extends @Nullable Object> arguments) {
            try {
                final Object o = method.invoke(schema.getTarget(), arguments.toArray());
                requireNonNull(o,
                        () -> "method " + method + " returned null for arguments " + arguments);
                return (TranslatableTable) o;
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Table based on a Java field.
     *
     * @param <T> element type
     */
    private static class FieldTable<T> extends ReflectiveTable {
        private final Field field;
        private Statistic statistic;

        FieldTable(Field field, Type elementType, Enumerable<T> enumerable) {
            this(field, elementType, enumerable, Statistics.UNKNOWN);
        }

        FieldTable(Field field, Type elementType, Enumerable<T> enumerable,
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

        @Override
        public Expression getExpression(SchemaPlus schema,
                                        String tableName, Class clazz) {
            ReflectiveSchema2 reflectiveSchema =
                    requireNonNull(schema.unwrap(ReflectiveSchema2.class),
                            () -> "schema.unwrap(ReflectiveSchema.class) for " + schema);
            return Expressions.field(
                    reflectiveSchema.getTargetExpression(
                            schema.getParentSchema(), schema.getName()), field);
        }
    }

    /** Function that returns an array of a given object's field values. */
    private static class FieldSelector implements Function1<Object, @Nullable Object[]> {
        private final Field[] fields;

        FieldSelector(Class elementType) {
            this.fields = elementType.getFields();
        }

        @Override
        public @Nullable Object[] apply(Object o) {
            try {
                final @Nullable Object[] objects = new Object[fields.length];
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
