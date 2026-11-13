package ch.paynash.pos.invoice;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import org.openpdf.text.pdf.BaseFont;

/**
 * The fonts embedded in invoice PDFs (design D9). Liberation Sans is bundled in {@code resources/fonts} so that umlauts
 * and amounts look the same on every system. License: {@code fonts/LiberationSans-LICENSE.txt} (SIL OFL 1.1).
 */
public final class PdfFonts {

	private static final BaseFont REGULAR = load("fonts/LiberationSans-Regular.ttf");

	private static final BaseFont BOLD = load("fonts/LiberationSans-Bold.ttf");

	private PdfFonts() {
	}

	public static BaseFont regular() {
		return REGULAR;
	}

	public static BaseFont bold() {
		return BOLD;
	}

	private static BaseFont load(String resource) {
		try (InputStream in = PdfFonts.class.getClassLoader().getResourceAsStream(resource)) {
			if (in == null) {
				throw new IllegalStateException("Font not found on the classpath: " + resource);
			}
			byte[] data = in.readAllBytes();
			return BaseFont.createFont(resource, BaseFont.IDENTITY_H, BaseFont.EMBEDDED, true, data, null);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

}
