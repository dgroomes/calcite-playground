package dgroomes;

import java.util.List;

/**
 * This is a schema-like dataset. Each field (list of objects) is like a table of rows.
 */
public class ClassRelationships {
    public final List<ClassInfo> classes;
    public final List<FieldInfo> fields;
    public final List<MethodInfo> methods;

    public ClassRelationships(List<ClassInfo> classes, List<FieldInfo> fields, List<MethodInfo> methods) {
        this.classes = classes;
        this.fields = fields;
        this.methods = methods;
    }

    @Override
    public String toString() {
        return "ClassRelationships{" +
                "classes=" + Util.formatInteger(classes.size()) +
                ", fields=" + Util.formatInteger(fields.size()) +
                ", methods=" + Util.formatInteger(methods.size()) +
                '}';
    }
}
