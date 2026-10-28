package ch.paynash.pos.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PinServiceTest {

	private final PinService pins = new PinService();

	@ParameterizedTest
	@ValueSource(strings = { "1234", "00000", "4711", "12345678" })
	void acceptsFourToEightDigits(String pin) {
		assertThat(PinService.isValidFormat(pin)).isTrue();
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = { "123", "123456789", "12a4", " 1234", "12 34", "-1234", "١٢٣٤" })
	void rejectsEverythingElse(String pin) {
		assertThat(PinService.isValidFormat(pin)).isFalse();
	}

	@Test
	void hashIsArgon2idAndVerifies() {
		String hash = pins.hash("4711");
		assertThat(hash).startsWith("$argon2id$").doesNotContain("4711");
		assertThat(pins.matches("4711", hash)).isTrue();
		assertThat(pins.matches("4712", hash)).isFalse();
		assertThat(pins.matches(null, hash)).isFalse();
	}

	@Test
	void samePinGivesDifferentHashes() {
		assertThat(pins.hash("4711")).isNotEqualTo(pins.hash("4711"));
	}

	@Test
	void refusesToHashInvalidPin() {
		assertThatThrownBy(() -> pins.hash("12")).isInstanceOf(IllegalArgumentException.class);
	}

}
