package dgroomes;

/**
 * This is purposely named after the {@link io.github.classgraph.ClassInfo} class in ClassGraph because it is an
 * excellent model, but I need a precise and small sub-scope of {@link io.github.classgraph.ClassInfo} to model the
 * data as a "table" for Apache Calcite.
 */
public class ClassInfo {
    public final String name;

    public ClassInfo(String name) {
        this.name = name;
    }
}
