package ch.paynash.pos.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SwissIdsTest {

	@Test
	void acceptsValidIbansAndRemovesSpaces() {
		assertThat(SwissIds.normalizeIban("CH93 0076 2011 6238 5295 7")).isEqualTo("CH9300762011623852957");
		assertThat(SwissIds.normalizeIban("li21 0881 0000 2324 013a a")).isEqualTo("LI21088100002324013AA");
	}

	@ParameterizedTest
	@ValueSource(strings = { "CH00 1234", "CH94 0076 2011 6238 5295 7", "DE89 3704 0044 0532 0130 00", "" })
	void rejectsInvalidIbans(String iban) {
		assertThat(SwissIds.normalizeIban(iban)).isNull();
	}

	@Test
	void acceptsValidUidsInCanonicalForm() {
		assertThat(SwissIds.normalizeVatUid("CHE-116.281.710 MWST")).isEqualTo("CHE-116.281.710 MWST");
		assertThat(SwissIds.normalizeVatUid("che116281710 mwst")).isEqualTo("CHE-116.281.710 MWST");
	}

	@ParameterizedTest
	@ValueSource(strings = { "CHE-116.281.711 MWST", "CHE-116.281.710", "CHE-116.281 MWST", "DE123456789" })
	void rejectsInvalidUids(String uid) {
		assertThat(SwissIds.normalizeVatUid(uid)).isNull();
	}

}
