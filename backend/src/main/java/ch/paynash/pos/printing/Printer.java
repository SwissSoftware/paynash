package ch.paynash.pos.printing;

/**
 * An Epson printer reached over ePOS-Print. {@code address} is a host or IP, optionally with port, or a full
 * {@code http(s)://} base URL. {@code printsTickets} means it prints the lines of the categories assigned to it,
 * {@code printsReceipts} that it prints invoice receipts (change printer-category-routing, design D1).
 */
public record Printer(long id, String name, String address, String deviceId, boolean printsTickets,
		boolean printsReceipts, boolean active) {
}
