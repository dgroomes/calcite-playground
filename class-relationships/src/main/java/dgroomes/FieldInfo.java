package dgroomes;

/**
 * Named after the {@link io.github.classgraph.FieldInfo}.
 */
public class FieldInfo {
    public final String NAME;
    public final ClassInfo owningClass;

    /**
     * It's redundant to store the owning class name, because it's already stored in the owning class object. However,
     * I need this to make Calcite joins work. I haven't figured out a better way yet.
     */
    public final String OWNINGCLASSNAME;

    public FieldInfo(String NAME, ClassInfo owningClass) {
        this.NAME = NAME;
        this.owningClass = owningClass;
        OWNINGCLASSNAME = owningClass.NAME;
    }
}
