package dgroomes;

/**
 * Named after the {@link io.github.classgraph.FieldInfo}.
 */
public class FieldInfo {
    public final String name;
    public final ClassInfo owningClass;

    /**
     * It's redundant to store the owning class name, but it's necessary to have a join column.
     */
    public final String owningClassName;
    public ClassInfo declaredClass; // TODO lazily set

    public FieldInfo(String name, ClassInfo owningClass) {
        this.name = name;
        this.owningClass = owningClass;
        owningClassName = owningClass.name;
    }
}
