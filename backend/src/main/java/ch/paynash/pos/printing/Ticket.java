package ch.paynash.pos.printing;

import java.util.ArrayList;
import java.util.List;

/**
 * A rendered ticket: rows of text, each printed large (double width and height) or normal. Stored in
 * {@code print_job.content} as one row per line, large rows prefixed with "# " and normal rows with two spaces, so a
 * reprint prints exactly the same and the stored text is readable.
 */
public record Ticket(List<Row> rows) {

	public record Row(boolean large, String text) {
	}

	public static Ticket decode(String content) {
		List<Row> rows = new ArrayList<>();
		for (String line : content.split("\n", -1)) {
			rows.add(new Row(line.startsWith("# "), line.length() >= 2 ? line.substring(2) : ""));
		}
		return new Ticket(rows);
	}

	public String encode() {
		var sb = new StringBuilder();
		for (Row row : rows) {
			if (!sb.isEmpty()) {
				sb.append('\n');
			}
			sb.append(row.large() ? "# " : "  ").append(row.text());
		}
		return sb.toString();
	}

	/** The same ticket with a REPRINT banner on top, so it is not mistaken for a new order. */
	public Ticket asReprint() {
		List<Row> marked = new ArrayList<>();
		marked.add(new Row(true, "*** REPRINT ***"));
		marked.addAll(rows);
		return new Ticket(marked);
	}

	static Builder builder() {
		return new Builder();
	}

	static final class Builder {

		private final List<Row> rows = new ArrayList<>();

		Builder large(String text) {
			rows.add(new Row(true, text));
			return this;
		}

		Builder normal(String text) {
			rows.add(new Row(false, text));
			return this;
		}

		Builder separator() {
			return normal("-".repeat(TicketRenderer.WIDTH));
		}

		Ticket build() {
			return new Ticket(List.copyOf(rows));
		}

	}

}
