package ch.paynash.pos.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class VatTest {

	@Test
	void standardRateOnAnEvenAmount() {
		assertThat(Vat.breakdown(List.of(new Vat.Amount(10810, 81))))
			.containsExactly(new Vat.Row(81, 10810, 10000, 810));
	}

	@Test
	void roundsHalfUpToTheRappen() {
		// 120.00 × 8.1 / 108.1 = 8.9916... → 8.99
		assertThat(Vat.breakdown(List.of(new Vat.Amount(12000, 81))))
			.containsExactly(new Vat.Row(81, 12000, 11101, 899));
		// 25.00 × 2.6 / 102.6 = 0.6335... → 0.63
		assertThat(Vat.breakdown(List.of(new Vat.Amount(2500, 26)))).containsExactly(new Vat.Row(26, 2500, 2437, 63));
	}

	@Test
	void sumsPerRateBeforeRoundingAndListsHighestRateFirst() {
		var rows = Vat.breakdown(List.of(new Vat.Amount(12000, 81), new Vat.Amount(5000, 0), new Vat.Amount(5000, 81),
				new Vat.Amount(2500, 26)));
		assertThat(rows).containsExactly(new Vat.Row(81, 17000, 15726, 1274), new Vat.Row(26, 2500, 2437, 63),
				new Vat.Row(0, 5000, 5000, 0));
	}

	@Test
	void noAmountsGiveNoRows() {
		assertThat(Vat.breakdown(List.of())).isEmpty();
	}

}
