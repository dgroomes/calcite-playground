package dgroomes;

/**
 * This is a schema-like dataset. Each field (array of objects) is like a table of rows.
 */
public class ClassRelationships {
    public final ClassInfo[] classes;
    public final FieldInfo[] fields;
    public final MethodInfo[] methods;

    public ClassRelationships(ClassInfo[] classes, FieldInfo[] fields, MethodInfo[] methods) {
        this.classes = classes;
        this.fields = fields;
        this.methods = methods;
    }

    @Override
    public String toString() {
        return "ClassRelationships{" +
                "classes=" + ClassRelationshipsRunner.formatInteger(classes.length) +
                ", fields=" + ClassRelationshipsRunner.formatInteger(fields.length) +
                ", methods=" + ClassRelationshipsRunner.formatInteger(methods.length) +
                '}';
    }
}
