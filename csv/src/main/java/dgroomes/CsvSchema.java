package dgroomes;

import org.apache.calcite.schema.Table;
import org.apache.calcite.schema.impl.AbstractSchema;
import org.apache.calcite.util.Source;
import org.apache.calcite.util.Sources;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * This is a Calcite {@link org.apache.calcite.schema.Schema} that is backed by CSV files. Each CSV file represents a
 * table.
 */
public class CsvSchema extends AbstractSchema {

    private static final Logger log = LoggerFactory.getLogger(CsvSchema.class);
    private final Map<String, Table> tableMap;

    /**
     * Create a CSV schema by scanning a directory for CSV files. Each CSV file represents a table.
     */
    public static CsvSchema create(File directory) {
        if (!directory.exists()) throw new AssertionError("There is no file named '%s'".formatted(directory));
        if (!directory.isDirectory()) throw new AssertionError("The file '%s' is not a directory".formatted(directory));

        Map<String, Table> _tableMap = new HashMap<>();

        try {
            // Traverse all the CSV files in the directory without descending into sub-folders
            Files.walkFileTree(directory.toPath(), Collections.emptySet(), 1, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                    log.trace("Visiting file: {}", path);

                    var fileName = path.getFileName().toString();
                    if (!fileName.endsWith(".csv")) return FileVisitResult.CONTINUE;

                    File file = path.toFile();
                    Source source = Sources.of(file);

                    // The file name without the ".csv" suffix is the table name
                    var tableName = fileName.substring(0, fileName.length() - 4);

                    log.debug("Adding table '{}' to the schema", tableName);
                    _tableMap.put(tableName, new CsvTable(source));
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            var msg = "Something went wrong while traversing the directory '%s'".formatted(directory);
            throw new RuntimeException(msg, e);
        }

        return new CsvSchema(Map.copyOf(_tableMap));
    }

    public CsvSchema(Map<String, Table> tableMap) {
        this.tableMap = tableMap;
    }

    @Override
    protected Map<String, Table> getTableMap() {
        return tableMap;
    }
}
