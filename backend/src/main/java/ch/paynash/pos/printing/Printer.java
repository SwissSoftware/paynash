package ch.paynash.pos.printing;

/**
 * An Epson printer reached over ePOS-Print. {@code address} is a host or IP, optionally with port, or a full
 * {@code http(s)://} base URL. {@code purpose} says whether it prints food or drink tickets, or invoice receipts.
 */
public record Printer(long id, String name, String address, String deviceId, PrintPurpose purpose, boolean active) {
}
