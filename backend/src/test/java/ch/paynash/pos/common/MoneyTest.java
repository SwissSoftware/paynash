package ch.paynash.pos.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class MoneyTest {

	@ParameterizedTest
	@CsvSource({ "0, 0", "0.00, 0", "4.50, 450", "4.5, 450", "4, 400", "18.00, 1800", "0.05, 5", "99999.99, 9999999" })
	void parsesValidAmounts(String input, int rappen) {
		assertThat(Money.parseRappen(input)).isEqualTo(rappen);
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "-1.00", "4.505", "4,50", "abc", "4.", ".50", "100000.00", "1e3" })
	void rejectsInvalidAmounts(String input) {
		assertThat(Money.parseRappen(input)).isNull();
	}

	@ParameterizedTest
	@CsvSource({ "0, 0.00", "5, 0.05", "450, 4.50", "1800, 18.00", "-900, -9.00" })
	void formatsWithTwoDecimals(long rappen, String expected) {
		assertThat(Money.format(rappen)).isEqualTo(expected);
	}

}
