package dgroomes;

import java.text.NumberFormat;
import java.util.Locale;

public class Util {
    /**
     * Formats a long value with commas.
     * <p>
     * For example, 1234567 becomes "1,234,567".
     */
    public static String formatInteger(long value) {
        return NumberFormat.getNumberInstance(Locale.US).format(value);
    }
}
