package dgroomes;

/**
 * Named after the {@link io.github.classgraph.FieldInfo}.
 */
public class FieldInfo {
    public final String name;
    public final ClassInfo owningClass;

    /**
     * It's redundant to store the owning class name, because it's already stored in the owning class object. However,
     * I need this to make Calcite joins work. I haven't figured out a better way yet.
     */
    public final String owningClassName;

    public FieldInfo(String name, ClassInfo owningClass) {
        this.name = name;
        this.owningClass = owningClass;
        owningClassName = owningClass.name;
    }
}
