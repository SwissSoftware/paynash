package ch.paynash.pos.common;

import java.util.List;

/**
 * Swiss VAT rates in tenths of a percent, 81 = 8.1 % (add-salon-mode design D6). The allowed rates live here and not
 * in the database, so a legal change needs only a code change.
 */
public final class VatRates {

	public static final int STANDARD = 81;

	/** Standard, reduced, accommodation, and no VAT. */
	public static final List<Integer> ALLOWED = List.of(81, 26, 38, 0);

	private VatRates() {
	}

	/** Parses "8.1", "8.1 %", or "0" into tenths of a percent; null if it is not an allowed rate. */
	public static Integer parse(String value) {
		if (value == null) {
			return null;
		}
		String v = value.replace("%", "").trim();
		if (!v.matches("\\d{1,2}(\\.\\d)?")) {
			return null;
		}
		int permille = v.contains(".") ? Integer.parseInt(v.replace(".", "")) : Integer.parseInt(v) * 10;
		return ALLOWED.contains(permille) ? permille : null;
	}

	/** Formats tenths of a percent as "8.1" or "0.0". */
	public static String format(int permille) {
		return permille / 10 + "." + permille % 10;
	}

}
