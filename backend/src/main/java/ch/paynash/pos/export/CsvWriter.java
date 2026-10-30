package ch.paynash.pos.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import ch.paynash.pos.common.Money;

/**
 * CSV as Excel reads it (spec order-export, "CSV encoding and escaping"): UTF-8 with a byte-order mark, comma
 * separator, CRLF line ends, and fields quoted per RFC 4180 when they contain a comma, quote, or line break.
 */
public final class CsvWriter implements AutoCloseable {

	private static final DateTimeFormatter ISO_WITH_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

	private final Writer out;

	public CsvWriter(OutputStream out) throws IOException {
		this.out = new OutputStreamWriter(out, StandardCharsets.UTF_8);
		this.out.write('﻿');
	}

	public void row(List<String> fields) throws IOException {
		for (int i = 0; i < fields.size(); i++) {
			if (i > 0) {
				out.write(',');
			}
			out.write(field(fields.get(i)));
		}
		out.write("\r\n");
	}

	static String field(String value) {
		if (value == null) {
			return "";
		}
		boolean quote = value.contains(",") || value.contains("\"") || value.contains("\n") || value.contains("\r");
		return quote ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
	}

	/** 9.00, -4.50: a dot and always two decimals. */
	public static String amount(long rappen) {
		return Money.format(rappen);
	}

	/** 2026-10-29T19:30:00+01:00 in the restaurant's time zone. */
	public static String timestamp(Instant instant, ZoneId zone) {
		return ISO_WITH_OFFSET.format(instant.atZone(zone).toOffsetDateTime());
	}

	@Override
	public void close() throws IOException {
		out.flush();
	}

}
