package ch.paynash.pos.invoice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PdfFontsTest {

	@Test
	void bundledFontsLoadAndCoverUmlauts() {
		assertThat(PdfFonts.regular().getPostscriptFontName()).isEqualTo("LiberationSans");
		assertThat(PdfFonts.bold().getPostscriptFontName()).isEqualTo("LiberationSans-Bold");
		for (char c : "ÄÖÜäöüéàè€".toCharArray()) {
			assertThat(PdfFonts.regular().charExists(c)).as("glyph %s", c).isTrue();
		}
	}

}
