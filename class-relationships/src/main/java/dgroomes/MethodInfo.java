package dgroomes;

import java.util.ArrayList;
import java.util.List;

/**
 * Named after the {@link io.github.classgraph.MethodInfo}.
 */
public class MethodInfo {
    public final String name;
    public final List<ClassInfo> parameterClasses = new ArrayList<>();
    public final ClassInfo returnClass;

    public MethodInfo(String name, ClassInfo returnClass) {
        this.name = name;
        this.returnClass = returnClass;
    }
}
