package ch.paynash.pos.common;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The form of a table label, shared by order labels and table numbers so a picked table and a typed label are
 * identical (change add-table-management, design D2).
 */
public final class TableLabels {

	private static final Pattern PATTERN = Pattern.compile("[A-Z0-9]{1,10}");

	private TableLabels() {
	}

	/**
	 * Trims and upper-cases {@code label}.
	 * @throws ApiException a validation error on {@code field} if the result is not 1 to 10 letters or digits
	 */
	public static String normalize(String label, String field, String what) {
		String normalized = label == null ? "" : label.trim().toUpperCase(Locale.ROOT);
		if (!PATTERN.matcher(normalized).matches()) {
			throw ApiException.validation(field, what + " must be 1 to 10 letters or digits");
		}
		return normalized;
	}

}
