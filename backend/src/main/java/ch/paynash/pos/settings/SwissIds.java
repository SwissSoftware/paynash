package ch.paynash.pos.settings;

import java.math.BigInteger;
import java.util.regex.Pattern;

/** Validation of Swiss business identifiers used on invoices. */
public final class SwissIds {

	private static final Pattern IBAN = Pattern.compile("(CH|LI)\\d{2}[0-9A-Z]{17}");

	private static final Pattern UID = Pattern.compile("CHE-?(\\d{3})\\.?(\\d{3})\\.?(\\d{3})\\s*MWST");

	private static final int[] UID_WEIGHTS = { 5, 4, 3, 2, 7, 6, 5, 4 };

	private SwissIds() {
	}

	/**
	 * Returns the IBAN without spaces and in upper case, or null if it is not a valid Swiss or Liechtenstein IBAN
	 * (format and ISO 7064 mod-97 check digits).
	 */
	public static String normalizeIban(String input) {
		if (input == null) {
			return null;
		}
		String iban = input.replace(" ", "").toUpperCase();
		if (!IBAN.matcher(iban).matches()) {
			return null;
		}
		String rearranged = iban.substring(4) + iban.substring(0, 4);
		var digits = new StringBuilder();
		for (char c : rearranged.toCharArray()) {
			digits.append(Character.isDigit(c) ? String.valueOf(c) : String.valueOf(c - 'A' + 10));
		}
		return new BigInteger(digits.toString()).mod(BigInteger.valueOf(97)).intValue() == 1 ? iban : null;
	}

	/**
	 * Returns the VAT number in the form "CHE-123.456.789 MWST", or null if the format or the mod-11 check digit is
	 * wrong.
	 */
	public static String normalizeVatUid(String input) {
		if (input == null) {
			return null;
		}
		var m = UID.matcher(input.trim().toUpperCase());
		if (!m.matches()) {
			return null;
		}
		String digits = m.group(1) + m.group(2) + m.group(3);
		int sum = 0;
		for (int i = 0; i < 8; i++) {
			sum += (digits.charAt(i) - '0') * UID_WEIGHTS[i];
		}
		int check = 11 - sum % 11;
		if (check == 11) {
			check = 0;
		}
		if (check == 10 || check != digits.charAt(8) - '0') {
			return null;
		}
		return "CHE-%s.%s.%s MWST".formatted(m.group(1), m.group(2), m.group(3));
	}

}
