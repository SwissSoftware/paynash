package ch.paynash.pos.export;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Spec order-export, "CSV encoding and escaping" and "CSV layout". */
class CsvWriterTest {

	private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

	@Test
	void startsWithABomAndEndsRowsWithCrLf() throws Exception {
		byte[] bytes = write(List.of("a", "b"), List.of("1", "2"));

		assertThat(Arrays.copyOf(bytes, 3)).containsExactly(0xEF, 0xBB, 0xBF);
		assertThat(text(bytes)).isEqualTo("a,b\r\n1,2\r\n");
	}

	@Test
	void quotesCommasQuotesAndLineBreaks() throws Exception {
		assertThat(text(write(List.of("ohne \"Zwiebeln\", bitte", "line 1\nline 2", "a\r\nb", "plain", ""))))
			.isEqualTo("\"ohne \"\"Zwiebeln\"\", bitte\",\"line 1\nline 2\",\"a\r\nb\",plain,\r\n");
	}

	@Test
	void missingValuesAreEmpty() throws Exception {
		assertThat(text(write(Arrays.asList("x", null, "y")))).isEqualTo("x,,y\r\n");
	}

	@Test
	void umlautsAreUtf8() throws Exception {
		byte[] bytes = write(List.of("Rösti", "Crème brûlée"));

		assertThat(Arrays.copyOfRange(bytes, 3, bytes.length))
			.isEqualTo("Rösti,Crème brûlée\r\n".getBytes(StandardCharsets.UTF_8));
	}

	@Test
	void amountsUseADotAndTwoDecimals() {
		assertThat(CsvWriter.amount(900)).isEqualTo("9.00");
		assertThat(CsvWriter.amount(450)).isEqualTo("4.50");
		assertThat(CsvWriter.amount(0)).isEqualTo("0.00");
		assertThat(CsvWriter.amount(123405)).isEqualTo("1234.05");
	}

	@Test
	void timestampsAreLocalWithOffset() {
		assertThat(CsvWriter.timestamp(Instant.parse("2026-10-29T18:30:00Z"), ZURICH))
			.isEqualTo("2026-10-29T19:30:00+01:00");
		assertThat(CsvWriter.timestamp(Instant.parse("2026-07-01T10:15:30Z"), ZURICH))
			.isEqualTo("2026-07-01T12:15:30+02:00");
	}

	@SafeVarargs
	private static byte[] write(List<String>... rows) throws Exception {
		var out = new ByteArrayOutputStream();
		try (var csv = new CsvWriter(out)) {
			for (List<String> row : rows) {
				csv.row(row);
			}
		}
		return out.toByteArray();
	}

	/** The content after the byte-order mark. */
	private static String text(byte[] bytes) {
		return new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
	}

}
