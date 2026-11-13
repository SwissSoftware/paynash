package ch.paynash.pos.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * VAT per rate on gross prices (add-salon-mode design D6): {@code vat = gross × rate / (100 + rate)}, rounded half up
 * to the Rappen, computed on the sum of the lines of each rate.
 */
public final class Vat {

	private Vat() {
	}

	/** One VAT rate on an invoice, amounts in Rappen. */
	public record Row(int ratePermille, long grossRappen, long netRappen, long vatRappen) {
	}

	/** An amount in Rappen and its VAT rate in tenths of a percent. */
	public record Amount(long rappen, int ratePermille) {
	}

	/** Rows ordered by rate, highest first. */
	public static List<Row> breakdown(List<Amount> amounts) {
		Map<Integer, Long> grossByRate = new TreeMap<>((a, b) -> Integer.compare(b, a));
		for (Amount a : amounts) {
			grossByRate.merge(a.ratePermille(), a.rappen(), Long::sum);
		}
		return grossByRate.entrySet().stream().map(e -> row(e.getKey(), e.getValue())).toList();
	}

	static Row row(int ratePermille, long grossRappen) {
		long vat = BigDecimal.valueOf(grossRappen)
			.multiply(BigDecimal.valueOf(ratePermille))
			.divide(BigDecimal.valueOf(1000L + ratePermille), 0, RoundingMode.HALF_UP)
			.longValueExact();
		return new Row(ratePermille, grossRappen, grossRappen - vat, vat);
	}

}
