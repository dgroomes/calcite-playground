package dgroomes;

import org.apache.calcite.DataContext;
import org.apache.calcite.adapter.file.CsvEnumerator;
import org.apache.calcite.adapter.java.JavaTypeFactory;
import org.apache.calcite.linq4j.AbstractEnumerable;
import org.apache.calcite.linq4j.Enumerable;
import org.apache.calcite.linq4j.Enumerator;
import org.apache.calcite.rel.type.RelDataType;
import org.apache.calcite.rel.type.RelDataTypeFactory;
import org.apache.calcite.schema.ScannableTable;
import org.apache.calcite.schema.impl.AbstractTable;
import org.apache.calcite.util.ImmutableIntList;
import org.apache.calcite.util.Source;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A Calcite {@link org.apache.calcite.schema.Table} backed by a CSV file.
 * <p>
 * This is similar to the official CSV example in the Calcite codebase.
 */
public class CsvTable extends AbstractTable implements ScannableTable {

    private final Source source;
    private RelDataType rowType;

    public CsvTable(Source source) {
        this.source = source;
    }

    @Override
    public RelDataType getRowType(RelDataTypeFactory typeFactory) {
        if (rowType == null) {
            // The Calcite CSV example implements this lazy initialization. I'm curious why it can't be done in the
            // constructor. It's clear that this is because the type factory isn't available that early (based on a
            // surface-level understanding of the API). But why?
            rowType = CsvEnumerator.deduceRowType((JavaTypeFactory) typeFactory, source, null, false);
        }
        return rowType;
    }

    @Override
    public Enumerable<Object[]> scan(DataContext root) {
        JavaTypeFactory typeFactory = root.getTypeFactory();
        List<RelDataType> fieldTypes = new ArrayList<>();
        CsvEnumerator.deduceRowType(typeFactory, source, fieldTypes, false);

        List<Integer> fields = ImmutableIntList.identity(fieldTypes.size());
        AtomicBoolean cancelFlag = DataContext.Variable.CANCEL_FLAG.get(root);
        return new AbstractEnumerable<>() {
            @Override
            public Enumerator<Object[]> enumerator() {
                return new CsvEnumerator<>(source, cancelFlag, false, null,
                        CsvEnumerator.arrayConverter(fieldTypes, fields, false));
            }
        };
    }
}
