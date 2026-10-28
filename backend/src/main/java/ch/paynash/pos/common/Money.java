package ch.paynash.pos.common;

import java.util.regex.Pattern;

/** CHF amounts are integer Rappen internally and "0.00" strings at the API boundary (design D6). */
public final class Money {

	private static final Pattern FORMAT = Pattern.compile("(\\d{1,5})(?:\\.(\\d{1,2}))?");

	private Money() {
	}

	/** Parses "4.5" or "4.50" to 450. Returns null for negative values, more than two decimals, or garbage. */
	public static Integer parseRappen(String value) {
		if (value == null) {
			return null;
		}
		var m = FORMAT.matcher(value.trim());
		if (!m.matches()) {
			return null;
		}
		String fraction = m.group(2) == null ? "00" : (m.group(2) + "0").substring(0, 2);
		return Integer.parseInt(m.group(1)) * 100 + Integer.parseInt(fraction);
	}

	public static String format(long rappen) {
		String sign = rappen < 0 ? "-" : "";
		long abs = Math.abs(rappen);
		return "%s%d.%02d".formatted(sign, abs / 100, abs % 100);
	}

}
